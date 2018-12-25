package io.sol.defaults;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sol.EventEmitter;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;

import static org.apache.kafka.common.config.TopicConfig.CLEANUP_POLICY_COMPACT;
import static org.apache.kafka.common.config.TopicConfig.CLEANUP_POLICY_CONFIG;

public class KafkaEventEmitter implements EventEmitter {

    private String bootstrapServers;
    private String logsTopic;
    private String sourcesTopic;
    private String commandsTopic;
    private KafkaProducer<byte[], byte[]> kafkaProducer;
    private Map<String, byte[]> globalKeys = new HashMap<>();
    private HashSet<String> enabledLoggers = new HashSet<>();
    private int numPartitions = 5;
    private final short replication = 1;
    private String appName;


    @Override
    public void configure(Map<String, ?> configs) {
        this.bootstrapServers = (String) configs.get("bootstrap.servers");
        this.logsTopic = (String) configs.get("log.topic");
        this.sourcesTopic = (String) configs.get("sources.topic");
        this.commandsTopic = (String) configs.get("commands.topic");
        this.appName = (String) configs.get("app.name");
        this.kafkaProducer = createProducer(bootstrapServers);

        Properties adminProps = new Properties();
        adminProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, this.bootstrapServers);
        AdminClient client = AdminClient.create(adminProps);
        try {
            if (client.listTopics().listings().get().stream().map(TopicListing::name).noneMatch(n -> n.equals(logsTopic))) {
                client.createTopics(Collections.singleton(new NewTopic(logsTopic, numPartitions, replication))).all().get();
                System.out.println("created logs topic");
            }
        } catch (Exception e) {
            System.err.println("Could not setup logs topic");
            e.printStackTrace();
        }

        try {
            if (client.listTopics().listings().get().stream().map(TopicListing::name).noneMatch(n -> n.equals(sourcesTopic))) {
                NewTopic newSourcesTopic = new NewTopic(sourcesTopic, 1, replication);
                Map<String, String> isCompacted = new HashMap<>();
                isCompacted.put(CLEANUP_POLICY_CONFIG, CLEANUP_POLICY_COMPACT);
                newSourcesTopic.configs(isCompacted);
                client.createTopics(Collections.singleton(newSourcesTopic)).all().get();
                System.out.println("created sources topic");
            }
        } catch (Exception e) {
            System.err.println("Could not setup sources topic");
            e.printStackTrace();
        }

        try {
            if (client.listTopics().listings().get().stream().map(TopicListing::name).noneMatch(n -> n.equals(commandsTopic))) {
                client.createTopics(Collections.singleton(new NewTopic(commandsTopic, 1, replication))).all().get();
                System.out.println("created commands topic");
            }

            Thread commandThread = new Thread(new CommandWatcher(), "command-thread");
            commandThread.setDaemon(true);
            commandThread.start();
        } catch (Exception e) {
            System.err.println("Could not setup commands topic");
            e.printStackTrace();
        }
    }

    @Override
    public void log(String name, Map<String, Object> event) {
        if (!enabledLoggers.contains(name)) {
            return;
        }

        byte[] globalLoggerKey = globalKeys.get(name);
        if (globalLoggerKey == null) {
            System.err.println("attempt to log with an unregistered logger");
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            byte[] vals = mapper.writeValueAsBytes(event);
            int partition = Math.abs(Arrays.hashCode(globalLoggerKey)) % numPartitions;
            kafkaProducer.send(new ProducerRecord<>(logsTopic, partition, System.currentTimeMillis(), globalLoggerKey, vals));
        } catch (Exception d) {
            System.err.println("Could not produce to Kafka. err=" + d.getMessage());
        }
    }

    @Override
    public void register(Map<String, Object> registration) {
        String name = (String) registration.get("logger_name");
        if (name == null) {
            System.err.println("registration does not contain logger_name?!" + registration);
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            byte[] loggerKey = mapper.writeValueAsBytes(new TreeMap<>(registration));
            globalKeys.put(name, loggerKey);
            kafkaProducer.send(new ProducerRecord<>(sourcesTopic, loggerKey, "{\"status\": \"enabled\"}".getBytes()));
        } catch (Exception d) {
            System.err.println("Could not register logger with sol. kafka err=" + d.getMessage());
        }
    }

    private static KafkaProducer<byte[], byte[]> createProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "sol-application-producer");
        props.put(ProducerConfig.ACKS_CONFIG, "0");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        return new KafkaProducer<>(props);
    }

    @Override
    public String toString() {
        return "KafkaEventEmitter{" +
                "bootstrapServers='" + bootstrapServers + '\'' +
                ", logsTopic='" + logsTopic + '\'' +
                '}';
    }

    class CommandWatcher implements Runnable {
        @Override
        public void run() {
            KafkaConsumer<String, String> consumer = createConsumer();
            consumer.subscribe(Collections.singleton(KafkaEventEmitter.this.commandsTopic));
            while (true) {
                try {
                    ConsumerRecords<String, String> consumed = consumer.poll(Duration.ofSeconds(5));
                    for (ConsumerRecord<String, String> rec : consumed) {
                        process(rec);
                    }
                } catch (WakeupException we) {
                    System.err.println("Woke up consumer. Safe to continue");
                } catch (InterruptException ie) {
                    System.err.println("Sol command thread interrupted. returning");
                    break;
                }
            }
        }

        private void process(ConsumerRecord<String, String> rec) {
            System.out.println("Received " + rec.key() + ", val " + rec.value());
            Map<String, Object> km = toMap(rec.key());
            Map<String, Object> vm = toMap(rec.value());
            if (vm.get("status").equals("enabled")) {
                String ln = (String) km.get("logger_name");
                System.out.println("enabling " + ln);
                KafkaEventEmitter.this.enabledLoggers.add(ln);
            } else if (vm.get("status").equals("disabled")) {
                String ln = (String) km.get("logger_name");
                System.out.println("disabling " + ln);
                KafkaEventEmitter.this.enabledLoggers.remove(ln);
            }
        }

        Map<String, Object> toMap(String val) {
            Map<String, Object> map;
            ObjectMapper mapper = new ObjectMapper();

            try {
                map = mapper.readValue(val, new TypeReference<HashMap>() {});
            } catch (IOException e) {
                System.err.println("Could not parse " + val);
                return new HashMap<>();
            }

            return map;
        }


        private KafkaConsumer<String, String> createConsumer() {
            final Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaEventEmitter.this.bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "sol-command-" + KafkaEventEmitter.this.appName + "-" + System.currentTimeMillis());
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            return new KafkaConsumer<>(props);
        }
    }
}

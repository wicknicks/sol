package io.sol.examples;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.util.Properties;

public class CommandProducer {

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "sol-test-command-producer");
        props.put(ProducerConfig.ACKS_CONFIG, "0");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(props);
        producer.send(new ProducerRecord<>("sol-commands", "{\"app_name\":\"App-with-a-Sol\",\"host\":{\"name\":\"arjun-desktop\",\"addr\":\"127.0.1.1\"},\"logger_name\":\"io.sol.examples.AppTest\"}".getBytes()
                , "{\"status\": \"disabled\"}".getBytes())).get();
    }
}

package io.sol.defaults;

import io.sol.EventEmitter;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class SolMain {

    static ConfigDef configDef = new ConfigDef()
            .define("bootstrap.servers", ConfigDef.Type.STRING, "", ConfigDef.Importance.HIGH, "kafka server bootstrap servers")
            .define("app.name", ConfigDef.Type.STRING, "app-noname", ConfigDef.Importance.MEDIUM, "name of the application")
            .define("log.topic", ConfigDef.Type.STRING, "sol-logs", ConfigDef.Importance.MEDIUM, "kafka topic to write application messages to.")
            .define("sources.topic", ConfigDef.Type.STRING, "sol-sources", ConfigDef.Importance.MEDIUM, "compacted Kafka topic to write application info to.")
            .define("commands.topic", ConfigDef.Type.STRING, "sol-commands", ConfigDef.Importance.MEDIUM, "Kafka topic to for commands for loggers.")
            ;

    private final EventEmitter emitter;
    private String appName = "app-noname";
    private Map<String, String> hostname = new HashMap<>();

    private static SolMain instance = new SolMain();

    private SolMain() {
        EventEmitter tmp = new NoopEventEmitter();
        try {
            tmp = loadConfig();
        } catch (IOException e) {
            System.err.println("Could not load config from solConfLocation");
        } finally {
            emitter = tmp;
        }

        try {
            InetAddress localhost = InetAddress.getLocalHost();
            hostname.put("name", localhost.getHostName());
            hostname.put("addr", localhost.getHostAddress());
        } catch (UnknownHostException e) {
            System.err.println("Could not find hostname" + e.getMessage());
            e.printStackTrace();
        }
    }

    public static SolMain get() {
        return instance;
    }

    public EventEmitter loadConfig() throws IOException {
        String solConfLocation = System.getProperty("sol.config");
        if (solConfLocation == null) {
            // try sol.conf instead..
            solConfLocation = System.getProperty("sol.conf");
        }
        System.out.println("Config file " + solConfLocation + (new File(solConfLocation).exists() ? " found " : " not found"));

        Map<String, Object> conf = configDef.parse(Utils.loadProps(solConfLocation));
        System.out.println(conf);

        this.appName = (String) conf.get("app.name");

        EventEmitter emitter = new KafkaEventEmitter();
        emitter.configure(conf);

        return emitter;
    }

    public EventEmitter emitter() {
        return emitter;
    }

    public void register(SolLoggerImpl solLogger) {
        Map<String, Object> registration = new HashMap<>();
        registration.put("app_name", appName);
        registration.put("logger_name", solLogger.name);
        registration.put("host", hostname);
        emitter.register(registration);
    }
}

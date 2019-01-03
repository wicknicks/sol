# Simple Object Logging

Sol is a library to dynamically log objects from a variety of sources and a framework to manage a large number of
such loggers running on servers in multiple data centers. It can be used to request objects from a Java application
or from a simple binary collecting metrics from a Linux server.

As a framework, Sol provides the following facilities:

1. Ability for loggers to identify themselves.
2. Ability for users to enable/disable these loggers.
3. Once enabled, loggers will emit objects into Kafka topics.

This repo contains the following two example loggers:

1. [Java application logger](sol-client/src/main/java/io/sol/).
2. [Go system metrics logger](go-sol/system-metrics/).

## Java Application Logger

In order to log objects with Sol, simply add the following lines anywhere in your application:

```java
public class AppTest {

    private final SolLogger sol = SolLoggers.logger(AppTest.class);

    public void start() {
        sol.log("hello", "world");
    }
}
```

The java program must be started with the JVM arg `-Dsol.conf=config/sol.properties`, where `sol.properties` is used to
configure all loggers created by Sol.

When `SolLogger` object (`sol`) is created, it attempts to register itself in a Kafka topic, producing a message
into a `sol-sources` topic, where the key is:

```json
{
  "app_name":"App-with-a-Sol",
  "host":
    {
      "name":"arjun-desktop",
      "addr":"127.0.1.1"
    },
  "logger_name":"io.sol.examples.AppTest",
  "type":"java_application_logger"
}
```

and the value is
```json
{
  "status": "ready"
}
```

The key above is used to identify a specific logger, and it's value gives us it's status. In this case, the logger is
created and is ready for commands.

Produce a message to the `sol-commands` topic where the key is the id of the logger (from above), and the value is:

```json
{
  "status": "enabled"
}
```

Now, any object logged using the `sol` object will be serialized and produced to the `sol-logs` Kafka topic. Consuming
from `sol-logs` will show the following values:

```bash
$ kafka-console-consumer --bootstrap-server localhost:9092 --topic sol-logs --from-beginning
...
{"hello": "world"}
{"hello": "world"}
{"hello": "world"}
{"hello": "world"}
{"hello": "world"}
...
^C Processed a total of 17 messages
```


Similarly, we can disable logging by setting the status to disabled to `sol-commands`:

```json
{
  "status": "disabled"
}
```

## System Metrics Collector

We have a Go based application that collects some system metrics, namely CPU and memory usage on the localhost and emits
those to `sol-logs`.

This application starts up and the Sol logger identifies itself as:

```json
{
  "app_name":"SolSystemMetrics",
  "logger_name":"CpuMemoryMetrics",
  "host": {
    "addr":"192.168.0.27",
    "name":"arjun-desktop"
  },
  "type":"example_go_logger"
}
```

Again, enable this by producing a message to `sol-commands` with the above key, and value as: `{"status": "enabled"}`
to start producing metrics into `sol-logs` Kafka. Consuming from `sol-logs` will show the following values:

```bash
$ kafka-console-consumer --bootstrap-server localhost:9092 --topic sol-logs --from-beginning
...
{"num_cpus":8,"cpu_usage_percent":99.7,"total_memory_bytes":8259698688,"free_memory_bytes":635236352,"free_memory_percent":7.6907935,"ts_millis":1546502663341}
{"num_cpus":8,"cpu_usage_percent":67.8,"total_memory_bytes":8259698688,"free_memory_bytes":634093568,"free_memory_percent":7.6769576,"ts_millis":1546502668359}
{"num_cpus":8,"cpu_usage_percent":57.300003,"total_memory_bytes":8259698688,"free_memory_bytes":629657600,"free_memory_percent":7.6232514,"ts_millis":1546502673375}
{"num_cpus":8,"cpu_usage_percent":51.700005,"total_memory_bytes":8259698688,"free_memory_bytes":625836032,"free_memory_percent":7.576984,"ts_millis":1546502678392}
{"num_cpus":8,"cpu_usage_percent":48.4,"total_memory_bytes":8259698688,"free_memory_bytes":624066560,"free_memory_percent":7.555561,"ts_millis":1546502683408}
...
^C Processed a total of 38 messages
```

## Next Steps

### Formalize

1. Formalize the protocol for communication between loggers and Sol.
2. Formalize the taxonomy of loggers' naming scheme.
3. Formalize schema/format of logged objects.

### Extend framework

1. Add serialization framework
2. Ability to configure loggers instead of simply enabling/disabling them? This could be an optional feature in select loggers.

### Ksql to manage collections of loggers

Since loggers are enabled by setting values in a Kafka topic, we might want to use Ksql and use regular expressions to
modify the status values on select loggers.

```sql
# Enable all loggers with type java_application_logger (hypothetical query)
UPDATE sol-commands SET value = {"status": "enabled"} where key.type = "java_application_logger"
```

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

### Example: Java Application Logger

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
configure all loggers created by Sol. The contents of the properties file is as follows:

```java
bootstrap.servers = kafka:9092
app.name = App-with-a-Sol
log.topic = sol-logs
```

After running this program, we can consume the `sol-logs` to find the following messages in it:

```bash
$ kafka-console-consumer --bootstrap-server localhost:9092 --topic sol-logs --from-beginning
{"hello": "world"}
^C Processed a total of 1 messages
```

If we print the key in this consumer, we will find a more detailed message explaining the origin of this message (**formatted for readability**):

```bash
$ kafka-console-consumer --bootstrap-server localhost:9092 --property print.key=true --topic sol-logs --from-beginning
*key*: {
  "app_name":"App-with-a-Sol",
  "host":
    {
      "name":"arjun-desktop",
      "addr":"127.0.1.1"
    },
  "logger_name":"io.sol.examples.AppTest",
  "type":"java_application_logger"
}

*value*: {"hello": "world"}
^C Processed a total of 1 messages
```

### Example: System Metrics Collector

We have a Go based application that collects some system metrics, namely CPU and memory usage on the localhost and emits
those to `sol-logs`. Building and running this application, and consuming from the `sol-logs` will show the following messages:

```bash
$ kafka-console-consumer --bootstrap-server localhost:9092 --topic sol-logs --from-beginning
{"num_cpus":8,"cpu_usage_percent":99.7,"total_memory_bytes":8259698688,"free_memory_bytes":635236352,"free_memory_percent":7.6907935,"ts_millis":1546502663341}
{"num_cpus":8,"cpu_usage_percent":67.8,"total_memory_bytes":8259698688,"free_memory_bytes":634093568,"free_memory_percent":7.6769576,"ts_millis":1546502668359}
{"num_cpus":8,"cpu_usage_percent":57.300003,"total_memory_bytes":8259698688,"free_memory_bytes":629657600,"free_memory_percent":7.6232514,"ts_millis":1546502673375}
{"num_cpus":8,"cpu_usage_percent":51.700005,"total_memory_bytes":8259698688,"free_memory_bytes":625836032,"free_memory_percent":7.576984,"ts_millis":1546502678392}
{"num_cpus":8,"cpu_usage_percent":48.4,"total_memory_bytes":8259698688,"free_memory_bytes":624066560,"free_memory_percent":7.555561,"ts_millis":1546502683408}
^C Processed a total of 5 messages
```
## Managing Loggers with Sol

Sol lets users manage a large number of such loggers running on various nodes in your data centers. Upon starting either of
the example applications, we can see the following topics automatically created by the applications:

```
$ kafka-topics --zookeeper zk:2181 --list
__consumer_offsets
sol-commands
sol-logs
sol-sources
```

The sol sources lists every active logger. Consuming the messages from the sol-sources shows all the active loggers and their status
(**formatted for readability**):

```json
$ kafka-console-consumer --bootstrap-server localhost:9092 --topic sol-sources --from-beginning --property print.key=true
*key*: {
  "app_name":"App-with-a-Sol",
  "host":
    {
      "name":"arjun-desktop",
      "addr":"127.0.1.1"
    },
  "logger_name":"io.sol.examples.AppTest",
  "type":"java_application_logger"
}

*value*: {"status": "ready"}
^CProcessed a total of 1 messages
```

Currently, the value in these messages only shows a `ready` status, but this could include more detailed metrics, error messages and logger configuration properties.

A logger can be sent commands via the `sol-commands` topic. Produce a message to the `sol-commands` topic where the key
is the id of the logger (from above), and the value requests the logger to be disabled (**formatted for readability**):

```json
$ kafka-console-producer --broker-list localhost:9092 --topic sol-sources --property "key.separator=->"
*key*: {
  "app_name":"App-with-a-Sol",
  "host":
    {
      "name":"arjun-desktop",
      "addr":"127.0.1.1"
    },
  "logger_name":"io.sol.examples.AppTest",
  "type":"java_application_logger"
}
->
*value*: {"status": "disabled"}
```

Similarly, enable the logger by changing the value to:

```json
{
  "status": "enabled"
}
```

The above commands produce records directly to Kafka topics to manage loggers. In the future, Sol binaries/scripts will
provide a more streamlined way to do the same. For example, sol list would list all apps:

```bash
$ sol list apps
App-with-a-Sol
SolSystemMetrics
```
and sol list loggers SolSystemMetrics, would list the loggers running within an app:

```bash
$ sol list loggers --app SolSystemMetrics
CpuMemoryMetrics
```

Enable or disable loggers with:

```bash
$ sol cmd --app SolSystemMetrics --logger CpuMemoryMetrics '{"status": "enabled"}'
OK
```

And check history of commands to a logger:

```bash
$ sol cmd --app SolSystemMetrics --logger CpuMemoryMetrics '{"status": "enabled"}'
[2019-01-06 16:08:39] {"status": "enabled"}
[2019-01-06 15:58:39] {"status": "disabled"}
[2019-01-06 15:40:09] {"status": "enabled"}
[2019-01-06 15:38:37] {"status": "disabled"}
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

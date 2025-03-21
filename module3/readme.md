# set up infrastructure

Copy content of config/docker to a directory where you can run docker.
Adjust docker-compose.yaml as needed. In particular, you may want to replace kafka.local with name of machine running docker.

```shell
cd dir/with/docker-compose.yaml
docker compose up -d
```

Настройка и тестирование коннектора описаны в разделе [Задание 2](#задание-2-создание-собственного-коннектора-для-переноса-данных-из-apache-kafka-в-prometheus) 

# Задание 1. Оптимизация параметров для повышения пропускной способности JDBC Source Connector
Goal: maximize Source Record Write rate

| Эксперимент | batch.size | linger.ms | compression.type | buffer.memory | Source Record Write Rate (кops/sec) |
| ----------- | ---------- | --------- | ---------------- | ------------- | ----------------------------------- |
| 1           | 12600      | 1000      | none             | 32Mb          | 39.6                                |
| 2           | 12600      | 1000      | snappy           | 32Mb          | 42.5                                |
| 3           | 12600      | 1000      | gzip             | 32Mb          | 39.5                                |
| 4           | 12600      | 1000      | lz4              | 32Mb          | 42.3                                |
| 5           | 12600      | 1000      | zstd             | 32Mb          | 39.6                                |
| 6           | 12600      | 1         | none             | 32Mb          | 36.8                                |
| 7           | 12600      | 0         | none             | 32Mb          | 25.4                                |
| 8           | 32768      | 1000      | none             | 32Mb          | 42.3                                |
metrics

| Name                     | query                                                           |
| ------------------------ | --------------------------------------------------------------- |
| Source Record Write rate | `rate(kafka_connect_source_task_source_record_write_total[1m])` |
| Record size average      | kafka_producer_record_size_avg                                  |
| Batch size average       | kafka_producer_batch_size_avg                                   |
| Buffer available memory  | kafka_producer_buffer_available_bytes                           |


Let's calculate batch.size

batch.max.rows = 100

We pass this parameter when creating JDBC source connector.

record_size_average_in_bytes = 126 bytes

The value is taken from grafana plots during testing.

recommended batch size

batch.size = 126 * 100 = 12600 bytes

Other parameters are set to defaults (except linger.ms)
- linger.ms = 1000
- compression.type = none
- buffer.memory = 32Mb
## experiment 1
We got the baseline for Source Record Write Rate - 39.9kops/sec
Buffer available bytes shows that significant portion of the buffer is free so there is no reason to increase buffer.memory.
avg. batch size is 12.6 Kb. It's equal to the maximum batch size. Hence linger.ms is large enough to assemble full buffer.
## experiment 2
Let's change compression.type. That is the only parameter that may improve performance in this case.
We change compression.type from none to snappy. I selected snappy because it should achieve good compression when moderate resource usage.

In my experiment all services run on the same machine so network delay must be very small. It's hard to say how compressing data should affect storage.

Target metric has improved slightly 39.6 -> 42.5
The change is very small. It can be attributed to how measurement points align to 15 second grafana grid.
## experiment 3-5
Let's try other compression.type: gzip, lz4, zstd
There are not many options so we can try them all.
It's hard to make predictions about how these algorithms may affect performance. There are competing factors: resource usage for compression vs reduced batch size.

Results show that is no significant change is performance.
## experiment 6
Let's decrease linger.ms to 1 ms.
We expect that this change reduce the target metric.
In fact the throughput has not changed very much. It dropped only to 36.8Kops/sec
## experiment 7
Let's decrease linger.ms to 0 ms.
That's the minimum value we can set.
This time throughput has dropped more significantly - 25.4Kops/sec. The avg batch size - 793 bytes - proves that producer does not assemble batch of maximum size.
## experiment 8
Let's increase batch.size to 32Kb.
The avg batch size is 32.7Kb.
Write rate did not change relative to baseline - 42.3kops/sec.
## summary
Experiments demonstrated that the baseline is best we can get. We can reduce resource usage by reducing buffer.memory because much of the buffer is unutilized.

# Задание 2. Создание собственного коннектора для переноса данных из Apache Kafka в Prometheus
## Project structure
 - PrometheusHttpServer - web server exporting metrics in prometheus data format
 - PrometheusSinkConnect - sink connector
 - PrometheusSinkTask - sink task
 - model/MetricEvent - data model used for deserializing incoming messages in JSON format.
 - utils/Dump - debug utils
 - pom.xml 
   - Note that connector was authored for a specific version of docker image confluentinc/cp-kafka-connect:7.7.1.
   The connector may not work correctly with other images.
- config/docker - configuration files for docker compose and containers
- config/kafka-connect/config.json - configuration files for creating instance of connector.

## Connector configuration
Use the command bellow to create connector. Instructions on testing the project are given in [Testing section](#testing).
This section only documents configuration settings.

post http://kafka.local:8083/connectors
```json
{
  "name": "prometheus-connector",
  "config": {
    "topics": "metrics-topic",
    "prometheus.listener.url": "http://localhost:8080/metrics",
    "tasks.max": "1",
    "connector.class": "org.example.module3.PrometheusSinkConnector",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.storage.StringConverter"
  }
}
```
- topics - kafka topic containing input messages

**prometheus.listener.url** 

url used to expose metrics in prometheus data format. Host name and schema are not used. 
You may specify e.g. ftp://yandex.ru:8080/metrics, but http server will handle only http requests on port 8080 with path /metrics.
So you can only customize port and path with url.

## Testing
create topic metrics-topic
```shell
docker exec kafka-0 kafka-topics.sh --create --topic metrics-topic --partitions 1 --replication-factor 2 --bootstrap-server localhost:9092
```

create .jar with maven
```sh
cd /project/root/dir
mvn clean package
```
result: `module3-prometheus-sink-connector-1.0-SNAPSHOT.jar`

drop module3-prometheus-sink-connector-1.0-SNAPSHOT.jar into confluent-hub-components directory on kafka.local machine

restart kafka-connect container
```sh
docker restart kafka-connect
```

wait until kafka-connect is ready

get http://kafka.local:8083/connector-plugins

it should list
```json
    {
        "class": "org.example.module3.PrometheusSinkConnector",
        "type": "sink",
        "version": "1.0.0"
    }
```

create connector

post http://kafka.local:8083/connectors
```json
{
  "name": "prometheus-connector",
  "config": {
    "topics": "metrics-topic",
    "prometheus.listener.url": "http://localhost:8080/metrics",
    "tasks.max": "1",
    "connector.class": "org.example.module3.PrometheusSinkConnector",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.storage.StringConverter"
  }
}
```
expected response: status code 201

check connector status

get http://kafka.local:8083/connectors/prometheus-connector/status

ensure that
- connector.state is running
- there is one task in running state (`tasks[0].state`)

expected output
```json
{
    "name": "prometheus-connector",
    "connector": {
        "state": "RUNNING",
        "worker_id": "localhost:8083"
    },
    "tasks": [
        {
            "id": 0,
            "state": "RUNNING",
            "worker_id": "localhost:8083"
        }
    ],
    "type": "sink"
}
```

get metrics

get http://kafka.local:8086/metrics

expected output
```
# Base URL: http://localhost:8080/metrics
```

send to metrics-topic with any key
```json
{
    "Alloc": {
        "Type": "gauge",
        "Name": "Alloc",
        "Description": "Alloc is bytes of allocated heap objects.",
        "Value": 24293912
    },
    "FreeMemory": {
        "Type": "gauge",
        "Name": "FreeMemory",
        "Description": "RAM available for programs to allocate",
        "Value": 7740977152
    }
}
```

query metrics again

get http://kafka.local:8086/metrics

expected output
```
# Base URL: http://localhost:8080/metrics
# HELP Alloc Alloc is bytes of allocated heap objects.
# TYPE Alloc gauge
Alloc 24293912.000000
# HELP FreeMemory RAM available for programs to allocate
# TYPE FreeMemory gauge
FreeMemory 7740977152.000000
```

We can see that connector processes messages and exposes them in prometheus format.

send to metrics-topic with any key
```json
{
    "Alloc": {
        "Type": "gauge",
        "Name": "Alloc",
        "Description": "Alloc is bytes of allocated heap objects.",
        "Value": 12
    }
}
```
expected output
```
# Base URL: http://localhost:8080/metrics
# HELP Alloc Alloc is bytes of allocated heap objects.
# TYPE Alloc gauge
Alloc 12.000000
# HELP FreeMemory RAM available for programs to allocate
# TYPE FreeMemory gauge
FreeMemory 7740977152.000000
```
Note that Alloc is updated to 12

## set up prometheus
The following snippet of prometheus/prometheus.yml is responsible scrapping metrics from our custom prometheus component.
prometheus/prometheus.yml in the config/docker directory already contains this fragment. No changes are needed.
```yaml
scrape_configs:
  - job_name: 'kafka-connect-module3'
    static_configs:
      - targets: ['kafka-connect:8080']
```

## view metrics in prometheus
- open prometheus UI in web browser http://kafka.local:9090
- open Graph panel
- find Alloc metric
- set interval to 1m
- click Execute
- you should see the current value of 12
- submit new message to metrics-topic with any key
```json
{
    "Alloc": {
        "Type": "gauge",
        "Name": "Alloc",
        "Description": "Alloc is bytes of allocated heap objects.",
        "Value": 20
    }
}
```
- click execute in prometheus ui
- You should see Alloc that metric changes from 12 to 20. Default scrape interval is 15 seconds. You may need to click the execute button a number of time before the metrics is updated.

# Задание 3. Получение лога вывода Debezium PostgresConnector
## set up logging to file
The task was to collect debezium logs. I redirected logs of io.debezium logger to debezium.log file.

I describe here how logs were collected. No need to reproduce.

extract /etc/confluent/docker/log4j.properties.template from kafka-connect container 
and add debezium appender to redirect debezium logs to a file.
```
log4j.appender.debezium=org.apache.log4j.RollingFileAppender
log4j.appender.debezium.File=/logs/debezium.log
log4j.appender.debezium.MaxFileSize=1MB
log4j.appender.debezium.MaxBackupIndex=1
log4j.appender.debezium.layout=org.apache.log4j.PatternLayout
log4j.appender.debezium.layout.ConversionPattern=[%d] %p %m (%c)%n
log4j.logger.io.debezium=INFO, debezium
```
complete file is config/kafka-connect/log4j.properties.template

updated log4j.properties.template is mounted to the original path in docker container
```yaml
  kafka-connect:
...
    volumes:
      - ./confluent-hub-components/:/etc/kafka-connect/jars
      - ./kafka-connect/log4j.properties.template:/etc/confluent/docker/log4j.properties.template
      - ./kafka-connect/logs:/logs
```

## Testing
start kafka-connect
```
docker start kafka-connect
```

create connector

put http://kafka.local:8083/connectors/debezium-connector/config
```json
{
"connector.class":"io.confluent.connect.jdbc.JdbcSourceConnector",
"tasks.max":"1",
"connection.url":"jdbc:postgresql://postgres:5432/customers?user=postgres-user&password=postgres-pw&useSSL=false",
"connection.attempts":"5",
"connection.backoff.ms":"50000",
"mode":"bulk",
"topic.prefix":"postgresql-jdbc-bulk-",
"table.whitelist": "users",
"poll.interval.ms": "20000"
}
```

insert a few records into users table
```sql
INSERT INTO users (id, name, private_info) 
VALUES (4, 'Den', 'Den@email.com');

INSERT INTO users (id, name, private_info) 
VALUES (5, 'Eve', 'Eve@email.com');
```

```sh
docker stop kafka-connect
```
## result
full log is logs/debezium.log

# Задание 1. Балансировка партиций и диагностика кластера
## project files
directory - assignment-1

docker-compose.yaml - slightly updated original docker-compose.yaml
## let's go
Launch bash in kafka container to get access to kafka utils.
```
docker exec -it kafka-1 bash
```

Создайте новый топик `balanced_topic` с 8 партициями и фактором репликации 3.
```
$ kafka-topics.sh --create --topic balanced_topic --partitions 8 --replication-factor 3 --bootstrap-server localhost:9092
```
Определите текущее распределение партиций.
```
$ kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic balanced_topic
Topic: balanced_topic   TopicId: PQP124ajSxWMoP5QKzVlLw PartitionCount: 8       ReplicationFactor: 3    Configs:
        Topic: balanced_topic   Partition: 0    Leader: 1       Replicas: 1,2,0 Isr: 1,2,0
        Topic: balanced_topic   Partition: 1    Leader: 2       Replicas: 2,0,1 Isr: 2,0,1
        Topic: balanced_topic   Partition: 2    Leader: 0       Replicas: 0,1,2 Isr: 0,1,2
        Topic: balanced_topic   Partition: 3    Leader: 1       Replicas: 1,0,2 Isr: 1,0,2
        Topic: balanced_topic   Partition: 4    Leader: 0       Replicas: 0,2,1 Isr: 0,2,1
        Topic: balanced_topic   Partition: 5    Leader: 2       Replicas: 2,1,0 Isr: 2,1,0
        Topic: balanced_topic   Partition: 6    Leader: 2       Replicas: 2,0,1 Isr: 2,0,1
        Topic: balanced_topic   Partition: 7    Leader: 0       Replicas: 0,1,2 Isr: 0,1,2
```

Создайте JSON-файл `reassignment.json` для перераспределения партиций.

let's generate reassignment.json with kafka-reassign-partitions.sh --generate option
input file: a.json
```json
{
  "version": 1,
  "topics": [
    {"topic": "balanced_topic"}
  ]
}
```

```
kafka-reassign-partitions.sh --bootstrap-server localhost:9092 --broker-list "0,1,2" --topics-to-move-json-file /share/a.json --generate
Current partition replica assignment
{"version":1,"partitions":[{"topic":"balanced_topic","partition":0,"replicas":[1,2,0],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":1,"replicas":[2,0,1],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":2,"replicas":[0,1,2],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":3,"replicas":[1,0,2],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":4,"replicas":[0,2,1],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":5,"replicas":[2,1,0],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":6,"replicas":[2,0,1],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":7,"replicas":[0,1,2],"log_dirs":["any","any","any"]}]}

Proposed partition reassignment configuration
{"version":1,"partitions":[{"topic":"balanced_topic","partition":0,"replicas":[1,2,0],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":1,"replicas":[2,0,1],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":2,"replicas":[0,1,2],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":3,"replicas":[1,0,2],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":4,"replicas":[2,1,0],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":5,"replicas":[0,2,1],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":6,"replicas":[1,2,0],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":7,"replicas":[2,0,1],"log_dirs":["any","any","any"]}]}
```

save proposed configuration to reassignment.json
```json
{
    "version": 1,
    "partitions": [{
            "topic": "balanced_topic",
            "partition": 0,
            "replicas": [1, 2, 0],
            "log_dirs": ["any", "any", "any"]
        }, {
            "topic": "balanced_topic",
            "partition": 1,
            "replicas": [2, 0, 1],
            "log_dirs": ["any", "any", "any"]
        }, {
            "topic": "balanced_topic",
            "partition": 2,
            "replicas": [0, 1, 2],
            "log_dirs": ["any", "any", "any"]
        }, {
            "topic": "balanced_topic",
            "partition": 3,
            "replicas": [1, 0, 2],
            "log_dirs": ["any", "any", "any"]
        }, {
            "topic": "balanced_topic",
            "partition": 4,
            "replicas": [2, 1, 0],
            "log_dirs": ["any", "any", "any"]
        }, {
            "topic": "balanced_topic",
            "partition": 5,
            "replicas": [0, 2, 1],
            "log_dirs": ["any", "any", "any"]
        }, {
            "topic": "balanced_topic",
            "partition": 6,
            "replicas": [1, 2, 0],
            "log_dirs": ["any", "any", "any"]
        }, {
            "topic": "balanced_topic",
            "partition": 7,
            "replicas": [2, 0, 1],
            "log_dirs": ["any", "any", "any"]
        }
    ]
}
```

Перераспределите партиции.

This reassignment does not bring any good to the cluster. Partitions replicas and leaders are evenly distributed among brokers. The only purpose of this operation is to demonstrate how to use the tool.

```
kafka-reassign-partitions.sh --bootstrap-server localhost:9092 --reassignment-json-file /share/reassignment.json --execute

Current partition replica assignment

{"version":1,"partitions":[{"topic":"balanced_topic","partition":0,"replicas":[1,2,0],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":1,"replicas":[2,0,1],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":2,"replicas":[0,1,2],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":3,"replicas":[1,0,2],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":4,"replicas":[0,2,1],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":5,"replicas":[2,1,0],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":6,"replicas":[2,0,1],"log_dirs":["any","any","any"]},{"topic":"balanced_topic","partition":7,"replicas":[0,1,2],"log_dirs":["any","any","any"]}]}

Save this to use as the --reassignment-json-file option during rollback
Successfully started partition reassignments for balanced_topic-0,balanced_topic-1,balanced_topic-2,balanced_topic-3,balanced_topic-4,balanced_topic-5,balanced_topic-6,balanced_topic-7
```

1. Проверьте статус перераспределения.
```
kafka-reassign-partitions.sh --bootstrap-server localhost:9092 --reassignment-json-file /share/reassignment.json --verify
Status of partition reassignment:
Reassignment of partition balanced_topic-0 is completed.
Reassignment of partition balanced_topic-1 is completed.
Reassignment of partition balanced_topic-2 is completed.
Reassignment of partition balanced_topic-3 is completed.
Reassignment of partition balanced_topic-4 is completed.
Reassignment of partition balanced_topic-5 is completed.
Reassignment of partition balanced_topic-6 is completed.
Reassignment of partition balanced_topic-7 is completed.

Clearing broker-level throttles on brokers 0,1,2
Clearing topic-level throttles on topic balanced_topic
```
Убедитесь, что конфигурация изменилась.
```
kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic balanced_topic
Topic: balanced_topic   TopicId: PQP124ajSxWMoP5QKzVlLw PartitionCount: 8       ReplicationFactor: 3    Configs:
        Topic: balanced_topic   Partition: 0    Leader: 1       Replicas: 1,2,0 Isr: 1,2,0
        Topic: balanced_topic   Partition: 1    Leader: 2       Replicas: 2,0,1 Isr: 2,0,1
        Topic: balanced_topic   Partition: 2    Leader: 0       Replicas: 0,1,2 Isr: 0,1,2
        Topic: balanced_topic   Partition: 3    Leader: 1       Replicas: 1,0,2 Isr: 1,0,2
        Topic: balanced_topic   Partition: 4    Leader: 0       Replicas: 2,1,0 Isr: 0,2,1
        Topic: balanced_topic   Partition: 5    Leader: 2       Replicas: 0,2,1 Isr: 2,1,0
        Topic: balanced_topic   Partition: 6    Leader: 2       Replicas: 1,2,0 Isr: 2,0,1
        Topic: balanced_topic   Partition: 7    Leader: 0       Replicas: 2,0,1 Isr: 0,1,2
```
For example, you may notice that replicas of partition 7 changed from 0,1,2 to 2,0,1

Смоделируйте сбой брокера:  
    a.  Остановите брокер `kafka-1`.  
```
docker stop kafka-1
```
    b.  Проверьте состояние топиков после сбоя.  
```
docker exec -it kafka-0 bash
kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic balanced_topic
Topic: balanced_topic   TopicId: PQP124ajSxWMoP5QKzVlLw PartitionCount: 8       ReplicationFactor: 3    Configs:
        Topic: balanced_topic   Partition: 0    Leader: 2       Replicas: 1,2,0 Isr: 2,0
        Topic: balanced_topic   Partition: 1    Leader: 2       Replicas: 2,0,1 Isr: 2,0
        Topic: balanced_topic   Partition: 2    Leader: 0       Replicas: 0,1,2 Isr: 0,2
        Topic: balanced_topic   Partition: 3    Leader: 0       Replicas: 1,0,2 Isr: 0,2
        Topic: balanced_topic   Partition: 4    Leader: 2       Replicas: 2,1,0 Isr: 0,2
        Topic: balanced_topic   Partition: 5    Leader: 0       Replicas: 0,2,1 Isr: 2,0
        Topic: balanced_topic   Partition: 6    Leader: 2       Replicas: 1,2,0 Isr: 2,0
        Topic: balanced_topic   Partition: 7    Leader: 2       Replicas: 2,0,1 Isr: 0,2
```
Notice that Isr is missing broker 1.

    c.  Запустите брокер заново.  

```
docker compose up kafka-1 -d
```

    d.  Проверьте, восстановилась ли синхронизация реплик.
    
```
kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic balanced_topic
Topic: balanced_topic   TopicId: PQP124ajSxWMoP5QKzVlLw PartitionCount: 8       ReplicationFactor: 3    Configs:
        Topic: balanced_topic   Partition: 0    Leader: 2       Replicas: 1,2,0 Isr: 2,0,1
        Topic: balanced_topic   Partition: 1    Leader: 2       Replicas: 2,0,1 Isr: 2,0,1
        Topic: balanced_topic   Partition: 2    Leader: 0       Replicas: 0,1,2 Isr: 0,2,1
        Topic: balanced_topic   Partition: 3    Leader: 0       Replicas: 1,0,2 Isr: 0,2,1
        Topic: balanced_topic   Partition: 4    Leader: 2       Replicas: 2,1,0 Isr: 0,2,1
        Topic: balanced_topic   Partition: 5    Leader: 0       Replicas: 0,2,1 Isr: 2,0,1
        Topic: balanced_topic   Partition: 6    Leader: 2       Replicas: 1,2,0 Isr: 2,0,1
        Topic: balanced_topic   Partition: 7    Leader: 2       Replicas: 2,0,1 Isr: 0,2,1
```
in-sync replicas восстановились.
All isr contain 3 brokers.
# Задание 2. Настройка защищённого соединения и управление доступом
## project files
directory - assignment-2

directory content
ca.cnf, kafka-0.cnf, kafka-2.cnf, kafka-2.cnf - openssl configuration files for generating certificates

create-cert.sh - shell script generating certificates
warning: the script clears /vagrant/kafka/module4 directory.

kafka-creds configuration files for kafka, zookeeper and command line utils

kafka-0-certs, kafka-1-certs, kafka-2-certs - directories containing certificates for broker 0, 1, 2 respectively
kafka-certs - CA certificates
## users
see kafka_server_jaas.conf

kadmin - cluster administrator
producer - producer's user
consumer - consumer's user
## set up kafka cluster
Copy following files and and directories to docker machine
- docker-compose.yaml
- kafka-0-certs
- kafka-1-certs
- kafka-2-certs
- kafka-certs
- kafka-creds

Other files are not need unless you want to recreate certificates

Ensure that there are no kafka-0, kafka-1, kafka-2, zookeeper containers and volumes specified in docker-compose.yaml. Otherwise cluster may fails to start.

cd to directory with docker-compose.yaml and execute
```
docker compose up -d
```
## creating topics and setting up permissions
execute
```
docker exec -it kafka-1 bash

kafka-topics --create --topic topic-1 --partitions 3 --replication-factor 2 --bootstrap-server localhost:9092 --command-config /etc/kafka/jaas/client-kadmin.properties

kafka-acls --add --allow-principal User:producer --producer --topic topic-1 --bootstrap-server localhost:9092 --command-config /etc/kafka/jaas/client-kadmin.properties

kafka-acls --add --allow-principal User:consumer --consumer --topic topic-1 --group '*' --bootstrap-server localhost:9092 --command-config /etc/kafka/jaas/client-kadmin.properties

kafka-acls --list --topic topic-1 --bootstrap-server localhost:9092 --command-config /etc/kafka/jaas/client-kadmin.properties
```

expected output of the last command
```
Current ACLs for resource `ResourcePattern(resourceType=TOPIC, name=topic-1, patternType=LITERAL)`:
        (principal=User:producer, host=*, operation=CREATE, permissionType=ALLOW)
        (principal=User:producer, host=*, operation=DESCRIBE, permissionType=ALLOW)
        (principal=User:consumer, host=*, operation=DESCRIBE, permissionType=ALLOW)
        (principal=User:producer, host=*, operation=WRITE, permissionType=ALLOW)
        (principal=User:consumer, host=*, operation=READ, permissionType=ALLOW)
```

execute
```
kafka-topics --create --topic topic-2 --partitions 3 --replication-factor 2 --bootstrap-server localhost:9092 --command-config /etc/kafka/jaas/client-kadmin.properties

kafka-acls --add --allow-principal User:producer --producer --topic topic-2 --bootstrap-server localhost:9092 --command-config /etc/kafka/jaas/client-kadmin.properties

kafka-acls --add --allow-principal User:consumer --operation Describe --topic topic-2 --group '*' --bootstrap-server localhost:9092 --command-config /etc/kafka/jaas/client-kadmin.properties

kafka-acls --list --topic topic-2 --bootstrap-server localhost:9092 --command-config /etc/kafka/jaas/client-kadmin.properties
```

```
Current ACLs for resource `ResourcePattern(resourceType=TOPIC, name=topic-2, patternType=LITERAL)`:
        (principal=User:producer, host=*, operation=WRITE, permissionType=ALLOW)
        (principal=User:producer, host=*, operation=CREATE, permissionType=ALLOW)
        (principal=User:consumer, host=*, operation=DESCRIBE, permissionType=ALLOW)
        (principal=User:producer, host=*, operation=DESCRIBE, permissionType=ALLOW)
```
## test permissions with cli tools
open shell in kafka-1 container
```
docker exec -it kafka-1 bash
```

send message to topic-1 as producer user (specified in client-producer.properties)
```
echo "hello 1" | kafka-console-producer --topic topic-1 --bootstrap-server localhost:9092 --producer.config /etc/kafka/jaas/client-producer.properties
```
receive the message from topic-1 as consumer user
```
kafka-console-consumer --topic topic-1 --from-beginning --bootstrap-server localhost:9092 --consumer.config /etc/kafka/jaas/client-consumer.properties
```
expected output
```
hello 1
```
Terminate consumer with Ctrl+C

producer and consumer as authorized to perform operation on topic-1

send message to topic-2 as producer user (specified in client-producer.properties)
```
echo "hello 2" | kafka-console-producer --topic topic-2 --bootstrap-server localhost:9092 --producer.config /etc/kafka/jaas/client-producer.properties
```

receive the message from topic-2 as consumer user
```
kafka-console-consumer --topic topic-2 --from-beginning --bootstrap-server localhost:9092 --consumer.config /etc/kafka/jaas/client-consumer.properties
```
this command should fail with a message like follows
```
[2025-04-08 06:02:29,240] WARN [Consumer clientId=console-consumer, groupId=console-consumer-36176] Not authorized to read from partition topic-2-0. (org.apache.kafka.clients.consumer.internals.FetchCollector)
[2025-04-08 06:02:29,246] ERROR Error processing message, terminating consumer process:  (org.apache.kafka.tools.consumer.ConsoleConsumer)
org.apache.kafka.common.errors.TopicAuthorizationException: Not authorized to access topics: [topic-2]
Processed a total of 0 messages
```
consumer user is not authorized to read topic-2

We can ensure that producer successfully sent message to topic-2 by executing consumer as kadmin user

```
kafka-console-consumer --topic topic-2 --from-beginning --bootstrap-server localhost:9092 --consumer.config /etc/kafka/jaas/client-kadmin.properties
```
expected output
```
hello 2
```

producer is authorized to perform operation on topic-2, but consumer is not authorized.

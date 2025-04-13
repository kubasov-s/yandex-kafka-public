# project structure
- app
Java producer and consumer.

Producer generates messages and sends them to Kafka in yandex cloud.
Consumer reads messages from the same topic and saves to local hadoop cluster.
- docker
Docker compose for local hadoop cluster. 

In addition to hadoop it include kafka and kafka-ui containers that were used for collecting info about kafka cluster in yandex cloud.
- data
It contains logs of
- java producer and consumer, 
- hadoop name and data nodes.
# Задание 1. Развёртывание и настройка Kafka-кластера в Yandex Cloud
## **Шаг 1.** Разверните Kafka

- Разверните кластер Kafka в Yandex Cloud с 3 брокерами.
- Укажите оптимальные параметры аппаратных ресурсов для брокеров (количество дисков, CPU, RAM).

Recommended configuration for production environment

| Component                                  | Nodes | Storage                                                                                 | Memory                      | CPU                  |
| ------------------------------------------ | ----- | --------------------------------------------------------------------------------------- | --------------------------- | -------------------- |
| Broker                                     | 3     | - 12 X 1 TB disk. RAID 10 is optional<br>- Separate OS disks from Apache Kafka® storage | 64 GB RAM                   | Dual 12-core sockets |
source: [Confluent Platform System Requirements](https://docs.confluent.io/platform/current/installation/system-requirements.html)

This configuration is overkill for a test environment where we only need to pass a few messages to demonstrate that components are connected properly. The bare minimum is our optimal configuration. We'll use the minimum configuration that we can select in yandex cloud for managed Kafka service. As you can see later this configuration can handle my test load.

Kafka configuration

| resource                   | amount      |
| -------------------------- | ----------- |
| ЦПУ                        | 2 vCPU      |
| оперативная память         | 8 ГБ        |
| тип диска                  | network-ssd |
| размер диска               | 32 Гб       |
| количество брокеров в зоне | 3           |
| реестр схем данных         | да          |
| публичный доступ           | да          |
Zookeeper configuration

| resource           | amount |
| ------------------ | ------ |
| ЦПУ                | 2 vCPU |
| оперативная память | 8 ГБ   |
## **Шаг 2.** Настройте репликацию и хранение данных:

- Создайте топик с 3 партициями и коэффициентом репликации 3.
- Настройте политику очистки логов (`log.cleanup.policy`)
- Установите параметры хранения (`log.retention.ms`, `log.segment.bytes`).

name: topic-1
Количество разделов: 3
Фактор репликации: 3
Политика очистки лога: delete
Время жизни сегмента лога, мс: 28800000
Размер файла сегмента лога, байт: 1048576

[log.cleanup.policy](https://kafka.apache.org/documentation/#brokerconfigs_log.cleanup.policy)
value: delete

Other log clean up policy - Data compaction - does fit our test data. Test data are user records. There is no user identifier which can be used to distinguish users and calculate the last value.

[log.retention.ms](https://kafka.apache.org/documentation/#brokerconfigs_log.retention.ms), [retention.ms](https://kafka.apache.org/documentation/#topicconfigs_retention.ms)
Let it be 8 hours = 8 * 60 * 60 * 1000 ms = 28800000 ms

[log.segment.bytes](https://kafka.apache.org/documentation/#brokerconfigs_log.segment.bytes), [segment.bytes](https://kafka.apache.org/documentation/#topicconfigs_segment.bytes)
The maximum size of a single log file

record sample
```json
{"name":"Yet another user","datetime":"2025-04-13T08:44:34.214908100"}
```
this record including metadata should fit into 256 bytes
input rate: 1 record every 3 seconds
over 8 hours
256 bytes * 8 * 60 * 60  / 3 = 2457600 bytes = 2.3 Mib
There are 3 partitions. Each partition should hold 2457600 bytes / 3 = 819200 bytes = 0.8 Mib
let it be 1Mib = 1048576
## **Шаг 3.** Настройте Schema Registry:
- Разверните Schema Registry.
- Зарегистрируйте схему данных.

Реестр схем данных развернут при создании сервиса Кафка.
Схема данные регистрируется автоматически при публикации сообщения. Она формируется по java-модели.
## **Шаг 4.** Проверьте работу Kafka:
- Напишите простой продюсер и консьюмер.
- Отправьте тестовые сообщения и убедитесь, что они передаются через Kafka.

продюсер и консьюмер расположены в папке app.
## пользователи kafka
name: producer 
разрешения: topic-1
роли: ACCESS_ROLE_PRODUCER

name: consumer
разрешения: topic-1
роли: ACCESS_ROLE_CONSUMER
## Выполненное задание 1 должно включать:
curl http://localhost:8081/subjects
curl --cacert CA.pem -u consumer:consumer-secret https://vm1.mdb.yandexcloud.net/subjects
```json
["topic-1-value"] 
```
curl --cacert CA.pem -u consumer:consumer-secret https://vm1.mdb.yandexcloud.net/subjects/topic-1-value/versions
```json
[1] 
```
Файл схемы .json
```json
{
	"$schema": "http://json-schema.org/draft-07/schema#",
	"additionalProperties": false,
	"properties": {
		"datetime": {
			"type": "string"
		},
		"name": {
			"type": "string"
		}
	},
	"required": [
		"name",
		"datetime"
	],
	"title": "User",
	"type": "object"
}
```
Получен через kafka-ui

- Вывод команды `kafka-topics.sh --describe`.
```
docker exec -it kafka-1 bash
kafka-topics --describe --topic topic-1 --bootstrap-server vm1.mdb.yandexcloud.net:9091 --command-config /share/client-producer.properties 
```

```
Topic: topic-1  TopicId: mSjxETTGTI25MuKo147z-Q PartitionCount: 3       ReplicationFactor: 3    Configs: min.insync.replicas=2,cleanup.policy=delete,segment.bytes=1048576,retention.ms=28800000
        Topic: topic-1  Partition: 0    Leader: 2       Replicas: 2,1,3 Isr: 2,1,3
        Topic: topic-1  Partition: 1    Leader: 3       Replicas: 3,2,1 Isr: 3,2,1
        Topic: topic-1  Partition: 2    Leader: 1       Replicas: 1,3,2 Isr: 1,3,2
```

- Код продюсера и консьюмера.
продюсер и консьюмер расположены в папке app.

- Скриншоты, подтверждающие успешную передачу сообщений:
	- логи продюсера, которые показывают успешную отправку сообщений;
`data/producer.log`
	- логи консьюмера, которые показывают, что сообщения успешно прочитаны.
`data/consumer.log`
# Задание 2. Интеграция Kafka с внешними системами (Apache NiFi / Hadoop)
We'll use local hadoop cluster for storing messages. See source code in docker directory.
## Выполненное задание 2 должно включать:

- Скриншот из консоли с запущенными сервисами.
```
$ docker ps
CONTAINER ID   NAMES               IMAGE                             COMMAND                  CREATED          STATUS
43522438c832   kafka-1             confluentinc/cp-kafka:7.4.4       "/etc/confluent/dock…"   30 minutes ago   Up 30 minutes
07d86985fa2a   hadoop-datanode-1   apache/hadoop:3.4.1               "/usr/local/bin/dumb…"   30 minutes ago   Up 30 minutes
6d53f05491cc   hadoop-datanode-2   apache/hadoop:3.4.1               "/usr/local/bin/dumb…"   30 minutes ago   Up 30 minutes
e2af634ecc6f   hadoop-datanode-3   apache/hadoop:3.4.1               "/usr/local/bin/dumb…"   30 minutes ago   Up 30 minutes
3b1c5894ebe5   hadoop-namenode     apache/hadoop:3.4.1               "/usr/local/bin/dumb…"   30 minutes ago   Up 30 minutes
f74bbb4e9858   zookeeper           confluentinc/cp-zookeeper:7.4.4   "/etc/confluent/dock…"   30 minutes ago   Up 30 minutes
ef2844564edf   kafka-kafka-ui-1    provectuslabs/kafka-ui:v0.7.0     "/bin/sh -c 'java --…"   30 minutes ago   Up 21 minutes
```
- Конфигурационные файлы запуска.
docker directory
- Код продюсера и консьюмера.
app directory
- Логи успешной передачи данных:
    - Kafka-топик с поступающими данными (`kafka-console-consumer.sh`);
data/consumer.log
    - логи успешной работы NiFi или Hadoop (вывод в консоли);
data/hadoop-namenode.log
data/hadoop-datanode-1.log
    - подтверждение записи данных (если используется HDFS или БД).
### note about hadoop logs
in data/hadoop-namenode.log you notice messages like follows
```
2025-04-13 06:11:13 INFO  BlockPlacementPolicy:925 - Not enough replicas was chosen. Reason: {NO_REQUIRED_STORAGE_TYPE=1}
2025-04-13 06:11:13 WARN  BlockPlacementPolicy:501 - Failed to place enough replicas, still in need of 1 to reach 3 (unavailableStorages=[], storagePolicy=BlockStoragePolicy{HOT:7, storageTypes=[DISK], creationFallbacks=[], replicationFallbacks=[ARCHIVE]}, newBlock=true) For more information, please enable DEBUG log level on org.apache.hadoop.hdfs.server.blockmanagement.BlockPlacementPolicy and org.apache.hadoop.net.NetworkTopology
```
Initially hadoop tries to find a node in another rack, fails and prints the warning, then fallback to local rack.
The following message confirms that block was saved.
```
2025-04-13 06:11:13 INFO  BlockStateChange:3777 - BLOCK* addStoredBlock: 172.19.0.5:9972 is added to blk_1073741832_1008 (size=72)
```
Since Replication factor is 3, there are 3 warnings and 3 addStoredBlock info for each saved message.

The second part of the log contains debug messages for `org.apache.hadoop.net.NetworkTopology` and `org.apache.hadoop.hdfs.server.blockmanagement.BlockPlacementPolicy` loggers.
Compare log fragments
- hadoop-namenode-fragment1.log
	- default logging
- hadoop-namenode-fragment2.log
	- selective debug logging

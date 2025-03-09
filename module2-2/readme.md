# Развертывание kafka-кластера

Copy docker-compose.yaml from config directory to a directory and machine where you can run docker.
Adjust docker-compose.yaml as needed. In particular, you may want to change EXTERNAL address
in KAFKA_CFG_ADVERTISED_LISTENERS if client application runs on separate machine.

```shell
cd dir/with/docker-compose.yaml
docker compose up -d
```

## создание топиков
```shell
docker exec -it kafka-0 bash
kafka-topics.sh --create --topic messages --partitions 3 --replication-factor 2 --bootstrap-server localhost:9092
```
## назначение и формат топиков
### messages
исходные сообщения

- ключ - string, любая строка, например, получатель сообщения
- значение - json-encoded сообщение со следующими полями
  - user_id - string, пользователь-отправитель сообщения
  - recipient_id - string, получатель сообщения
  - message - string, сообщение
  - timestamp - bigint, время отправки сообщения, unixtimestamp in milliseconds

например
```json
{
  "user_id": "user-1",
  "recipient_id": "r-2",
  "message": "hello",
  "timestamp": 1741282694494
}
```

# загрузка тестовых данных
Test data are stored in test-data directory.
Copy messages.ndjson and messages2.ndjson to share directory next to docker-compose.yaml 
and load messages.ndjson into messages topic with the following command. 
Do not load messages2.ndjson now, we need it later.
```sh
docker exec -it kafka-0 bash
cat /share/messages.ndjson | kafka-console-producer.sh --topic messages --bootstrap-server localhost:9092
```

## test data structure
There are 10 users: user-1, ..., user-10. 
- user-1 produced 1 message, 
- user-2 - 2 messages, 
- ...
- user-10 - 10 messages.


- user-1 communicated with 1 recipient, 
- user-2 - 1 recipient, 
- user-3 - 2 recipients, 
- ...
- user-10 - 5 recipients

55 messages total

# testing
KSQL queries are stored in ksqldb-queries.sql are assignment requested. I duplicate those queries here with a few comments.

To run ksql queries, use kafka-ui, KSQL DB tab. You can open kafka ui on http://localhost:8080/.

I set `auto.offset.reset=earliest` to force `select (push query)` process existing data.
This mode is more convenient for testing since otherwise we need to load new data after every query to get results.
To test that tables are updated when new data arrives, we import second chunk of data 
and check that tables are updated accordingly. 

```sql
-- создание исходного потока
CREATE STREAM messages_stream (
    user_id STRING,
    recipient_id STRING,
    message STRING,
    timestamp BIGINT
) WITH (
    KAFKA_TOPIC='messages',
    VALUE_FORMAT='JSON',
    PARTITIONS=3
);
```

```sql
-- создание таблицы общего количества отправленных сообщений;
create table messages_total as
select 1, count(*) count
from messages_stream
group by 1
emit changes;
```

```sql
-- создание таблицы, подсчитывающей количество уникальных получателей для всех сообщений;
create table unique_recipient as
select 1, count_distinct(recipient_id) count
from messages_stream
group by 1
emit changes;
```

```sql
-- создание таблицы, подсчитывающей количество сообщений, отправленных каждым пользователем;
-- Создайте таблицу `user_statistics` для агрегирования данных по каждому пользователю.
create table user_statistics as
select user_id, count(*) message_count, count_distinct(recipient_id) recipient_count
from messages_stream
group by user_id
emit changes;
```

```sql
-- создайте таблицу 5 наиболее активных пользователей (по количеству отправленных сообщений).
create table top_users as
select 1,
  transform(
    slice(
      array_sort(map_values(transform(histogram(user_id), (k, v) => k, (k, v) => lpad(cast(v as string), 3, '0') + k)), 'desc'),
      1, 
      least(5, cast(count_distinct(user_id) as int))
    ),
    v => substring(v, 4)
  ) users
from messages_stream
group by 1
emit changes;
```
The first approach relies on `histogram` function.

A few notes on implementation.

- `transform(histogram(user_id), (k, v) => k, (k, v) => lpad(cast(v as string), 3, '0') + k)` - build a map 
with string value `{number of messages}{user id}`. Number of messages is 0-padded to length 3.
For example, user-1 with 1 message becomes '001user-1'. 
This format allows to sort user ids by number of messages. 
3 characters for number of messages is enough for our test case. 
We can increase this value as needed if we expect more data.
- map_values - extract map values to array.
- array_sort - sort array of encoded user ids in the order of decreasing number of messages.
- `transform(..., v => substring(v, 4))` - extract username. First 3 characters - number of messages.
- slice - take first 5 items.
- `least(5, cast(count_distinct(user_id) as int))` - calculate number of items to keep in the result.
We can't use 5 because slice returns null if the second argument is out of array bounds.
- `histogram` counts up to 1000 items. That is enough for our 10-user case.

```sql
create table top_users2 as
select 1,
  transform(
    slice(
      array_sort(map_values(transform(as_map(collect_list(user_id), collect_list(message_count)), (k, v) => k, (k, v) => lpad(cast(v as string), 3, '0') + k)), 'desc'),
      1, 
      least(5, cast(count(user_id) as int))
    ),
    v => substring(v, 4)
  ) users
from user_statistics
group by 1
emit changes;
```
The second approach relies on user_statistics table.
Implementation is similar to the first approach. Some differences:

- We create user - message count map explicitly: `as_map(collect_list(user_id), collect_list(message_count))`
- We can use `count(user_id)` instead of `count_distinct(user_id)` because users are unique.
- `COLLECT_LIST` collects at most 1000 items. That is the same limit as we have with `histogram`.

```sql
-- Реализуйте запросы:
-- подсчёт количества сообщений, отправленных каждым пользователем;
select user_id, message_count
from user_statistics;
```

Expected results
- user-1 - 1 message
- user-2 - 2 messages
- ...
- user-10 - 10 messages

```sql
-- подсчёт количества уникальных получателей для каждого пользователя;
select user_id, recipient_count
from user_statistics;
```
Expected results
- user-1 - 1 recipient
- user-2 - 1 recipient
- ...
- user-10 - 5 recipients

```sql
-- тестовые запросы в ksqlDB, чтобы проверить результаты:
-- вывести общее количество отправленных сообщений;
select count
from messages_total
emit changes;
```
Expected results:
55 messages

```sql
-- вывести уникальных получателей;
select count
from unique_recipient
emit changes;
```
Expected results:
5 recipients

```sql
-- вывести без потокового обновления 5 наиболее активных пользователей.
select users
from top_users;
```
Expected results:
user-10, user-9, user-8, user-7, user-6

Let's add 19 new messages for user-1 (test-data/messages2.ndjson) to demonstrate that tables are updating.
```sh
docker exec -it kafka-0 bash
cat /share/messages2.ndjson | kafka-console-producer.sh --topic messages --bootstrap-server localhost:9092
```

new messages update statistic as follows

55 + 19 = 74 messages total

user-1 
- 20 messages
- 10 recipients

Other users won't change.

```sql
select *
from user_statistics;
```
user-1
- 20 messages
- 10 recipients

```sql
select count
from messages_total;
```
Expected results:
74

```sql
select count
from unique_recipient
```
Expected results:
10

```sql
select users
from top_users;
```
Expected results:
user-1, user-10, user-9, user-8, user-7

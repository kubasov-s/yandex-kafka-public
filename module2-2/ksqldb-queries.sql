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

-- создание таблицы общего количества отправленных сообщений;
create table messages_total as
select 1, count(*) count
from messages_stream
group by 1
emit changes;

-- создание таблицы, подсчитывающей количество уникальных получателей для всех сообщений;
create table unique_recipient as
select 1, count_distinct(recipient_id) count
from messages_stream
group by 1
emit changes;

-- создание таблицы, подсчитывающей количество сообщений, отправленных каждым пользователем;
-- Создайте таблицу `user_statistics` для агрегирования данных по каждому пользователю.
create table user_statistics as
select user_id, count(*) message_count, count_distinct(recipient_id) recipient_count
from messages_stream
group by user_id
emit changes;

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

-- Реализуйте запросы:
-- подсчёт количества сообщений, отправленных каждым пользователем;
select user_id, message_count
from user_statistics;

-- подсчёт количества уникальных получателей для каждого пользователя;
select user_id, recipient_count
from user_statistics;

-- тестовые запросы в ksqlDB, чтобы проверить результаты:
-- вывести общее количество отправленных сообщений;
select count
from messages_total
emit changes;

-- вывести уникальных получателей;
select count
from unique_recipient
emit changes;

-- вывести без потокового обновления 5 наиболее активных пользователей.
select users
from top_users;

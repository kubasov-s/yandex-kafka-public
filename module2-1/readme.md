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
kafka-topics.sh --create --topic filtered_messages --partitions 3 --replication-factor 2 --bootstrap-server localhost:9092
kafka-topics.sh --create --topic blocked_users --partitions 3 --replication-factor 2 --bootstrap-server localhost:9092
kafka-topics.sh --create --topic blocked_words --partitions 3 --replication-factor 2 --bootstrap-server localhost:9092
```

## назначение и формат топиков
### messages
исходные сообщения

- ключ - получатель сообщения, непример, bob
- значение - json-encoded отправитель сообщение и текст сообщения

например
```json
{
  "message": "Hi, Bob. This is Alice",
  "fromUser": "alice"
}
```

### filtered_messages
Сообщения, прошедшие обработку.

Удалены сообщения, соответствующие фильтру пользователей.
В тексте сообщения запрещенные слова заменены ****.

ключ и значение имеют такую же структуру, как в топике исходных сообщений - messages

### blocked_users
блокировки пользователей

- ключ - получатель
- значение - json-list заблокированных отправителей сообщения

например
- ключ: bob
- значение: `["trudy", "mike"]`

bob блокирует сообщение от trudy и mike

для изменения набора заблокированных пользователей, отправляем сообщение новыми списком
заблокированных пользоватлей

### blocked_words
запрещенные слова

- ключ: заблокированнае слово
- значение: любая непустая строка

заблокированное слово состоять только из word characters `[a-zA-Z_0-9]`. 
В противном случае слово не будет найдено.

для блокировки слова abcd, отправляем в топик сообщение
- ключ: abcd
- значение: x

для разблокировки слова abcd, отправляем в топик сообщение
- ключ: abcd
- значение:

# Настройка приложения
Настройка выполняется в файле application.yaml
Здесь можно изменить endpoint для подключения к Kafka - service.boostrapServersConfig - 
и названия топиков.

# Запуск приложения
Проект можно запустить через Intellij IDEA
или через консоль:
```
cd /project/root/dir
mvn clean package
java -jar .\target\module2_1-0.0.1-SNAPSHOT.jar
```

# структура проекта
- model - модели сообщений для топика messages и blocked_users
- serdes - сериализаторы и десериализаторы
- service - сервисы. Основная логика приложения реализован здесь.

- MainService - главный сервис приложения. Он отвечает за создание топологии и запуск Kafka Streams.
- CensorshipProcessor - обработка запрещенный слов в теле сообщения

Более подробно работу приложения см. в исходном коде.

# тестирование
Для отправки и просмотра сообщений можно использовать kafka-ui или утилиты командной строки.

изначально нет никаких фильтров, поэтому все сообщения должны копировать из входного топика messages в
выходной топик filtered_messages без изменений

отправляем сообщение в messages

key: bob
```json
{
  "message": "Hi, Alice",
  "fromUser": "alice"
}
```

В консоли получаем
```
input: (bob, Message(message=Hi, Alice, fromUser=alice))
process message: Hi, Alice
output: (bob, Message(message=Hi, Alice, fromUser=alice))
```
из логов видим, что сообщение доставлено в выходной топик

можно также проверить содержимое filtered_messages в kafka_ui

проверяем фильтрацию пользователей
в топик blocked_users отправляем сообщение

key: bob
```json
["alice"]
```

bob блокирует сообщения alice

отправляем сообщение в messages

key: bob
```json
{
  "message": "Hi, Alice. message2",
  "fromUser": "alice"
}
```

логи показывают, что сообщение было удалено
```
input: (bob, Message(message=Hi, Alice. message2, fromUser=alice))
удалено сообщение: (bob, Message(message=Hi, Alice. message2, fromUser=alice))
```

изменяем фильтр пользователей
в топик blocked_users отправляем сообщение

key: bob
```json
["trudy"]
```

отправляем сообщение в messages

key: bob
```json
{
  "message": "Hi, Alice. message3",
  "fromUser": "alice"
}
```

логи показывают, что сообщение было доставлено
```
input: (bob, Message(message=Hi, Alice. message3, fromUser=alice))
process message: Hi, Alice. message3
output: (bob, Message(message=Hi, Alice. message3, fromUser=alice))
```

тестируем цензурирование сообщений
добавим запрещенные слова в blocked_words

- key: security
- value: x

- key: problems
- value: x

отправляем сообщение в messages

key: somebody
```json
{
  "message": "Alice, Bob, and Trudy are fictional characters often used in cryptography to illustrate different scenarios and problems related to security and confidentiality. While they may seem like mere characters, they represent valid problems that must be addressed to implement a comprehensive security solution.",
  "fromUser": "trudy"
}
```

output message will be
```
Alice, Bob, and Trudy are fictional characters often used in cryptography to illustrate different scenarios and **** related to **** and confidentiality. While they may seem like mere characters, they represent valid **** that must be addressed to implement a comprehensive **** solution.
```

security и problems были заменены в тексте сообщения

now change blocked words

- key: security
- value:

- key: characters
- value: x

отправляем повторно то же сообщение

key: somebody
```json
{
  "message": "Alice, Bob, and Trudy are fictional characters often used in cryptography to illustrate different scenarios and problems related to security and confidentiality. While they may seem like mere characters, they represent valid problems that must be addressed to implement a comprehensive security solution.",
  "fromUser": "trudy"
}
```

output message will be
```
Alice, Bob, and Trudy are fictional **** often used in cryptography to illustrate different scenarios and **** related to security and confidentiality. While they may seem like mere ****, they represent valid **** that must be addressed to implement a comprehensive security solution.
```
"security" has passed through, but "characters" was replaced with ****

# замечания к реалзиации требований
> Для каждого пользователя создайте отдельный список заблокированных пользователей. Список должен храниться на диске.

Список заблокированных пользователей - тело сообщения в топике blocked_users.
Сообщения топика аккумулируются в KTable<String, UserSet> blockedUsersTable,
которая сохраняется на диске.

Возможно имелось ввиду, что список заблокированных пользователей должен загружаться из файла на диске.
В таком случае нет необходимости в топике blocked_users. 

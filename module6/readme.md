# file system structure
root directory contains the following subdirectories
- app
	- java services. See [[#Java services]] for more details
- data
	- collection of product files for importing via shop app
- doc
	- documentation. Architectural diagram.
- docker
	- docker compose project
# architecture
![[architecture.png]]
This diagram shows overview of component constituting the solution. You can see service names, kafka topics and other items. Monitoring system, schema registry are not shown.
# docker service
## other containers
-  kafka-1, kafka-2, kafka-3
	- Kafka services of the master cluster
-  kafka-m1, kafka-m2, kafka-m3
	- Kafka services of the slave cluster. Letter m means mirror.
- schema-registry
	- schema registry
- kafka connect
	- it contains file sink that copies public-product topic into product.txt file.
- mirror-maker
	- mirror maker that copy master cluster into slave cluster. Mirroring is limited to client-request and public-product topics.
- kafka-ui
	- helper container. It's useful to inspect kafka topics and schema registry.
	- http://kafka.local:8082
- hadoop-namenode
	- name node of hadoop cluster
	- http://kafka.local:9870
- hadoop-datanode-1, hadoop-datanode-2, hadoop-datanode-3
	- data nodes of hadoop cluster
- prometheus
	- it collects metrics of master and slave clusters.
	- http://kafka.local:9090
## grafana
It visualize Prometheus metrics and fires alarms when kafka nodes are down.

http://kafka.local:3000

login: admin
password: kafka

There are a number of dashboard with kafka metrics.

"kafka status" dashboard shows status of Kafka cluster. It can be useful for testing alarm system.

Node state is evaluated every minute. Alert is fired if node is down for a minute. State updates are emailed every minutes as well. If the current state is alerting and a node goes down or up, you'll receive another email with updated state in a minute.
# Kafka topics

## product
original products from shops
- key - product name
- value - product details in JSON format

cluster: master
## banned-product
list of banned products
- key - product name
- value - Boolean - whether the product is banned

cluster: master
## public-product
Products that passed banned-product filter.

Structure is identical to the `product` topic.

cluster: master and slave
## client-request
Client search requests - products names client app looked up.
- key - null
- value - product name
cluster: master and slave
## recommendation
Recommended products.
- key - null
- value - array of products names
cluster: slave
# Java services
## shop
It implements shop API. It loads products from .json files into the product topic of the master cluster.

usage
```
module6-shop <file>... <directory>...
```
Each file should contain a single product in JSON format. File name and extension does not matter.
Directory processing is not recursive.
## ban-product
CLI application for managing banned product.
It writes information about banned products into the banned-product topic of the master cluster.

usage
```
module6-ban-product OPTIONS
Options
--ban=<product name> - ban product
--unban=<product name> - unban product
```
You can ban/unban a number of products at once.
## product-filter
It's Kafka streams applications that copies product topic to public-product topic skipping products in the banned-product topic.
## client
It implements client API.
It implement two function: look up products and request recommendations.

usage:
```
usage:  
client OPTIONS  
  
Options:  
--product=<product name> - look up product  
--recommend - get recommended products
```
To lookup products, run client with --product option, specifying product name. The application looks up the specified product in product.txt file. Each client request is registered in the client-request topic.

To request recommendations, run client with --recommend option. The application reads the last record from recommendation topic.
## import-to-hadoop
It copies client-request and public-product topics of the slave cluster to hadoop.

Client requests are saved in `/data/request-<guid>` files.

Products are saved in `/data/product-<guid>` files.

File content is identical to the value of the corresponding topic in utf-8 encoding.
## analytics
It reads products and client requests from hdfs, generates recommended list of products, and submit recommendations into recommendation topic of the slave cluster.

Recommendation algorithm
- load all products from hdfs
- load all client requests from hdfs
- find all products requested by client
- select product categories
- find all products in the selected categories
- select 3 random products from the list
# set up docker project
Copy content of the docker directory to docker machine.
## SMTP settings
specify SMTP settings in compose.yaml file in grafana service
```yaml
  grafana:
    image: grafana/grafana:11.6.1
    restart: unless-stopped
    ports:
     - 3000:3000
    environment:
      GF_PATHS_DATA: /var/lib/grafana
      GF_SECURITY_ADMIN_PASSWORD: kafka
      GF_SMTP_ENABLED: true
      GF_SMTP_HOST: smtp.yandex.ru:465
      GF_SMTP_USER: nobody@yandex.ru
      GF_SMTP_PASSWORD: secret
      GF_SMTP_FROM_ADDRESS: nobody@yandex.ru
```
For yandex email, you need to specify user, password, and "from address".

Specify "from address" in `docker\grafana\provisioning\alerting\alert_resources.yaml`
It should identical to the email in compose.yaml.
```yaml
apiVersion: 1

contactPoints:
  - orgId: 1
    name: grafana-alerting-email
    receivers:
      - uid: grafana-alerting-email_uid
        type: email
        settings:
          addresses: nobody@yandex.ru <<<< change this
          #message: '{{ template "custom_email.message" .}}'
          singleEmail: false
          #subject: '{{ template "custom_email.subject" .}}'
        disableResolveMessage: false
```

Without correct SMTP setting grafana alerting will not function.
## certificates
All required certificates are included with the docker compose project. We assume that docker services run on kafka.local host, otherwise you may need to regenerate certificates.

You can regenerate certificates with create-cert.sh.
- Configuration files: `config/*.cnf`.  
- Script to regenerate certificates: docker/scripts/create-cert.sh
## jars
docker/jars/jmx_prometheus_javaagent-1.2.0.jar
Download this file from https://prometheus.github.io/jmx_exporter/1.2.0/java-agent/

docker/confluent-hub-components/connect-file-3.9.0.jar
Download this file from https://mvnrepository.com/artifact/org.apache.kafka/connect-file/3.9.0
## product.txt
product.txt is log of kafka connect file sink plugin.

It's attached to container as follows.

```
- /vagrant/kafka/big-data:/share2
```
The output file is /vagrant/kafka/big-data/product.txt

You should set the host part to a directory accessible for java services. Specify path to product.txt in application.yaml of client app. See [[#set up java services]].
## start project
Once all pieces are in place you can start project with `docker compose up -d`

init service should create all required kafka topics and set up permissions. 

Wait till init service terminates. It may take a few minutes.

check exit code
```
docker inspect module6-init-1 | jq '.[0].State'
```
Exit code should be 0 and there is no error.

The exact service name depends on the current directory.

### init project manually
If automatic initialization fails, you may run initialization scripts manually.
Script are idempotent and can be executed multiple times. Scripts are attached to kafka-1 and kafka-m1 containers.

master cluster
```sh
docker exec -it kafka-1 bash
export KAFKA_OPTS=
/scripts/set-up-cluster.sh
```

slave cluster
```sh
docker exec -it kafka-m1 bash
export KAFKA_OPTS=
/scripts/set-up-cluster.sh
```

register product file sink connector
```sh
curl -X PUT --data-binary "@/config/product-file-sink.json" -H 'Content-Type: application/json' http://kafka.local:8083/connectors/product-file-sink/config`
```
## inspecting cluster
You can inspect cluster with 
- kafka-ui - http://kafka.local:8082
- grafana - http://kafka.local:3000
	- login: admin
	- password: :kafka
	- kafka status dashboard
- hadoop - http://kafka.local:9870
- prometheus - http://kafka.local:9090

in the master cluster you should see the following topics
- banned-product
- client-request
- product
- public-product
in the slave cluster you should see the following topics
- client-request
- public-products
- recommendation

curl http://kafka.local:8083/connectors/product-file-sink/status
expected output
```json
{
    "name": "product-file-sink",
    "connector": {
        "state": "RUNNING",
        "worker_id": "localhost:8083"
    },
    "tasks": [{
            "id": 0,
            "state": "RUNNING",
            "worker_id": "localhost:8083"
        }
    ],
    "type": "sink"
}
```
# set up java services
Java services are located in apps folder.

Each service has its own configuration file - application.yaml.

You need to specify path to `kafka-certs\kafka.truststore.jks`. Default path is 'C:\module6\docker\kafka-certs\kafka.truststore.jks'. This file is used by every service.

In the client app you need to specify path to product.txt file. Default path is `C:\module6\product.txt`. This file is generated by file sink plugin of kafka-connect.

Build app with commands
```powershell
cd app
mvn clean package


cd analytics
mvn clean package
cd ..
```
`analytics` must be built separately because it's the only java 11 app, other - java 17 apps.
# console encoding
Since Java apps print Cyrillic characters it's vital to ensure that text is rendered correctly.

Apps are configured to use utf-8 encoding as follows

application.yaml
```yaml
logging.charset.console: UTF-8
```

You can configure utf-8 charset in windows consoles as follows.

PowerShell Core 7.2.2
```powershell
$OutputEncoding = [console]::InputEncoding = [console]::OutputEncoding = New-Object System.Text.UTF8Encoding
```

cmd and PowerShell 5.1
```cmd
chcp 65001
```

# testing
## product filter
start product filter
```
java -jar .\product-filter\target\module6-product-filter-1.0.0-SNAPSHOT.jar
```
This service should be running all the time of testing.
## product import
In another console import product1.json
```
java -jar .\shop\target\module6-shop-1.0.0-SNAPSHOT.jar C:\module6\data\product1.json
```
expected output
```
loaded file C:\module6\data\product1.json to kafka topic product
```

open kafka-ui - http://kafka.local:8082
You should see the imported product in
- product topic of master cluster
- public-product topic of master and slave clusters

- key: Умные часы XYZ
- value: `{"product_id":"12345","name":"Умные часы XYZ",...`

The product was saved in the input product topic, copied to public-product topic by product-filter service, copied to the slave cluster with mirror-maker.
## banning product
Let's ban 'Умные часы XYZ' 

execute the command
```
java -jar .\ban-product\target\module6-ban-product-1.0.0-SNAPSHOT.jar --ban="Умные часы XYZ"
```
banned-topic should include the following record
- key: Умные часы XYZ
- value: true

Let's update product id to 12346 and import the product1.json again

```
java -jar .\shop\target\module6-shop-1.0.0-SNAPSHOT.jar C:\module6\data\product1.json
```

expected output 
```
loaded file C:\module6\data\product1.json to kafka topic product
```
"Умные часы XYZ" with product id 123456 will appear in product topic but not in the public-product topic.

Let's unban "Умные часы XYZ"
```
java -jar .\ban-product\target\module6-ban-product-1.0.0-SNAPSHOT.jar --unban="Умные часы XYZ"
```
banned-topic should include the following record
- key: Умные часы XYZ
- value: false
## product.txt log
Let's import all products
```
java -jar .\shop\target\module6-shop-1.0.0-SNAPSHOT.jar C:\module6\data
```

expected output
```
loaded file C:\module6\data\product1.json to kafka topic product
loaded file C:\module6\data\product2.json to kafka topic product
loaded file C:\module6\data\product3.json to kafka topic product
loaded file C:\module6\data\product4.json to kafka topic product
loaded file C:\module6\data\product5.json to kafka topic product
loaded file C:\module6\data\product6.json to kafka topic product
loaded files in directory C:\module6\data
```
Now public-product topic should contain 6 products in both clusters.

You should see the imported products in
- product topic of master cluster
- public-product topic of master and slave clusters

product.txt file should contain all 6 products + 1 product that we imported initially.
Line looks like follows
```
Struct{store_id=store_001,images=[Struct{alt=Умные часы XYZ - вид спереди...
```
## look up product with client app
Let's look up "Умные часы XYZ" with client app

```
java -jar .\client\target\module6-client-1.0.0-SNAPSHOT.jar --product="Умные часы XYZ"
```

expected output
```
looking up product: Умные часы XYZ
found 2 results
> Struct{store_id=store_001,images=[Struct{alt=Умные часы XYZ - вид спереди,url=https://example.com/images/product1.jpg}, Struct{alt=Умные часы XYZ - вид сбоку,url=https://example.com/images/product1_side.jpg}],description=Умные часы с функцией мониторинга здоровья, GPS и уведомлениями.,created_at=2023-10-01T12:00:00Z,index=products,specifications=Struct{},tags=[умные часы, гаджеты, технологии],updated_at=2023-10-10T15:30:00Z,price=Struct{amount=4999.99,currency=RUB},product_id=12346,name=Умные часы XYZ,category=Электроника,stock=Struct{reserved=20,available=150},sku=XYZ-12345,brand=XYZ}
> Struct{store_id=store_001,images=[Struct{alt=Умные часы XYZ - вид спереди,url=https://example.com/images/product1.jpg}, Struct{alt=Умные часы XYZ - вид сбоку,url=https://example.com/images/product1_side.jpg}],description=Умные часы с функцией мониторинга здоровья, GPS и уведомлениями.,created_at=2023-10-01T12:00:00Z,index=products,specifications=Struct{},tags=[умные часы, гаджеты, технологии],updated_at=2023-10-10T15:30:00Z,price=Struct{amount=4999.99,currency=RUB},product_id=12346,name=Умные часы XYZ,category=Электроника,stock=Struct{reserved=20,available=150},sku=XYZ-12345,brand=XYZ}
```

client-request topic now contains "Умные часы XYZ"
## load data into hdfs
let's load products and client requests to hdfs

```
java -jar .\import-to-hadoop\target\module6-import-to-hadoop-1.0.0-SNAPSHOT.jar
```

You should notice messages like follows
```
received product message: {product_id=524740537, ...
message saved to hdfs: /data/product-70b00674-b819-472d-b186-13c34e59889f

received client request message: Умные часы XYZ
message saved to hdfs: /data/request-16637f0f-79ca-4b9d-8c54-91f345c9d042
```

In hdfs you should see a number of `product-{guid}` and single `request-{guid}` file in data directory.
To view hdfs, open hadoop UI - http://kafka.local:9870 - and navigate to Utilities % Browse the file system.

Importing service runs until it's manually terminated. Open another console to continue testing.
## generate recommendations (analytics)

This app requires java 11.
```powershell
& "C:\Program Files\Java\jdk-11.0.14\bin\java.exe" -jar .\analytics\target\module6-analytics-1.0.0-SNAPSHOT.jar
```

expected output
```
Generated product recommendations: [Умные часы XYZ, Фотоаппарат Fujifilm, Тостер Kitfort КТ-6027]
Sent recommended product list [Умные часы XYZ, Фотоаппарат Fujifilm, Тостер Kitfort КТ-6027] to topic recommendation
```
Recommendation algorithm is not deterministic. Selected products will likely be different.

Check recommendation topic of the slave cluster. You should see the same list products that were logged on console.
## request recommendations with client app
Request recommendations with client app

```
java -jar .\client\target\module6-client-1.0.0-SNAPSHOT.jar --recommend
```

expected output
```
getting recommendations...
recommended products:
> Умные часы XYZ
> Фотоаппарат Fujifilm
> Тостер Kitfort КТ-6027
```
Note that set of recommended products will likely be different.
## monitoring system
open grafana - http://kafka.local:3000
login: admin
password: kafka

There are a number of dashboards dedicated to different aspects of kafka performance.

There is single instance of grafana and prometheus that collect metrics from both master and slave clusters. You can distinguish clusters by host name.
- kafka-1, kafka-2, kafka-3 - main cluster
- kafka-m1, kafka-m2, kafka-m3 - slave cluster

"kafka status" dashboard is helpful for inspecting brokers states.
When cluster is healthy all brokers are "up".
![[doc/grafana-dashboard-service-is-down.png]]
## alerting
Let's stop kafka-2 for example
```
docker compose stop kafka-2
```
10-15 seconds later you'll that kafka-2 is down on grafana dashboard.

Let's switch to Home % Alerting % Alerting rules % service is down alert
It's state should be pending.
In a minute or so state changes to Firing and you receive a email about the issue.

`[FIRING:1] service is down kafka (kafka-2:9876 kafka)`

labels

| **alertname**      | service is down |
| ------------------ | --------------- |
| **grafana_folder** | kafka           |
| **instance**       | kafka-2:9876    |
| **job**            | kafka           |

Alert is firing after server has being down for 1 minute.

let's start kafka-2

```
docker compose up kafka-2 -d
```
"kafka status" dashboard shows that all services are "up".

"service is down" alert changes to normal.

You'll receive another email notifying that the issue is resolved.

`[RESOLVED] service is down kafka (kafka-2:9876 kafka)`
labels

| **alertname**      | service is down |
| ------------------ | --------------- |
| **grafana_folder** | kafka           |
| **instance**       | kafka-2:9876    |
| **job**            | kafka           |

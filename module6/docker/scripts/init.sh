#!/usr/bin/bash
set -e
if [ -e /etc/confluent/docker/kafka-init ]; then
  echo "kafka is already initialized"
  exit 0;
fi
export KAFKA_OPTS="-Dkafka.logs.dir=/tmp -Xmx256M -Xms256M"

cub kafka-ready -b kafka-1:9092,kafka-2:9092,kafka-3:9092 -c /kafka-creds/client-admin.properties 3 300
echo "set up master cluster"
/scripts/set-up-master-cluster.sh

cub kafka-ready -b kafka-m1:9092,kafka-m2:9092,kafka-m3:9092 -c /kafka-creds/client-admin.properties 3 300
echo "set up slave cluster"
/scripts/set-up-slave-cluster.sh

cub connect-ready kafka-connect 8083 300
echo "set up kafka connect"
a=`curl -X PUT --data-binary "@/config/product-file-sink.json" -H 'Content-Type: application/json' -o /tmp/kafka-connect.log -s -w "%{http_code}" http://kafka-connect:8083/connectors/product-file-sink/config`
if [ "x$a" != "x200" -a "x$a" != "x201" ]; then
  echo "failed to set up kafka connect. Status code $a"
  cat /tmp/kafka-connect.log
  exit 1
fi
touch /etc/confluent/docker/kafka-init

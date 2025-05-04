#!/bin/sh
set -e
bootstrap_server=kafka-m1:9092

kafka-acls --add --allow-principal User:hadoop_consumer --consumer --topic public-product --group 'app.import-to-hadoop' --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-acls --add --allow-principal User:hadoop_consumer --consumer --topic client-request --group 'app.import-to-hadoop' --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-topics --create --if-not-exists --topic recommendation --partitions 3 --replication-factor 3 --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-acls --add --allow-principal User:analytics_producer --producer --topic recommendation --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-acls --add --allow-principal User:client --consumer --topic recommendation --group 'app.client' --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-acls --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties --add \
               --allow-principal User:schema_registry --allow-host '*' \
               --producer --consumer --topic _schemas --group schema-registry

kafka-acls --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties --add \
               --allow-principal User:schema_registry --allow-host '*' \
               --operation DescribeConfigs --topic _schemas

kafka-acls --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties --add \
               --allow-principal User:schema_registry --allow-host '*' \
               --operation Describe --topic _schemas

kafka-acls --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties --add \
               --allow-principal User:schema_registry --allow-host '*' \
               --operation Read --topic _schemas

kafka-acls --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties --add \
               --allow-principal User:schema_registry --allow-host '*' \
               --operation Write --topic _schemas

kafka-acls --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties --add \
               --allow-principal User:schema_registry --allow-host '*' \
               --operation Describe --topic __consumer_offsets

kafka-acls --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties --add \
               --allow-principal User:schema_registry --allow-host '*' \
               --operation Create --cluster kafka-cluster

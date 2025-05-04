#!/bin/sh
set -e
bootstrap_server=kafka-1:9092

kafka-topics --create --if-not-exists --topic product --partitions 3 --replication-factor 3 --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-acls --add --allow-principal User:shop --producer --topic product --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-topics --create --if-not-exists --topic public-product --partitions 3 --replication-factor 3 --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-topics --create --if-not-exists --topic banned-product --partitions 3 --replication-factor 3 --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-acls --add --allow-principal User:product_filter --consumer --topic product --group 'app.product-filter' --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-acls --add --allow-principal User:product_filter --producer --topic public-product --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-acls --add --allow-principal User:product_filter --consumer --topic banned-product --group 'app.product-filter' --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-acls --add --allow-principal User:product_filter --operation All --topic 'app.product-filter' --group 'app.product-filter' --resource-pattern-type prefixed --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-acls --add --allow-principal User:kafka_connect --consumer --topic public-product --group 'connect-product-file-sink' --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-topics --create --if-not-exists --topic client-request --partitions 3 --replication-factor 3 --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

kafka-acls --add --allow-principal User:client --producer --topic client-request --bootstrap-server $bootstrap_server --command-config /kafka-creds/client-admin.properties

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

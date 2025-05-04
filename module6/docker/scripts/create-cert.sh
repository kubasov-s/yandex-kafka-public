#!/bin/sh
set -e

mkdir -p kafka-certs
rm -f kafka-certs/*

echo "3 CA certificate"
# create certificate
openssl req -new -nodes \
   -x509 \
   -days 365 \
   -newkey rsa:2048 \
   -keyout kafka-certs/ca.key \
   -out kafka-certs/ca.crt \
   -config config/ca.cnf
# output
# ca.key - private key
# ca.crt - certificate

echo "4 ca.pem"
cat kafka-certs/ca.crt kafka-certs/ca.key > kafka-certs/ca.pem

broker_cert() {
  local no=$1
  echo "5 broker $no"
  local cert_dir=kafka-$no-certs
  mkdir -p $cert_dir
  rm -f $cert_dir/*

  echo "6 certificate signing request"
  openssl req -new \
      -newkey rsa:2048 \
      -keyout $cert_dir/kafka.key \
      -out $cert_dir/kafka.csr \
      -config config/kafka-$no.cnf \
      -nodes
  # kafka-1.key - private key
  # kafka-1.csr - certificate request
  # -nodes - do not encrypt private key

  echo "7 broker certificate signed by CA"
  openssl x509 -req \
      -days 3650 \
      -in $cert_dir/kafka.csr \
      -CA kafka-certs/ca.crt \
      -CAkey kafka-certs/ca.key \
      -CAcreateserial \
      -out $cert_dir/kafka.crt \
      -extfile config/kafka-$no.cnf \
      -extensions v3_req
  # kafka-1.crt - new certificate

  # confluent 6.
  # We’ll need to convert the server certificate over to the pkcs12 format
  echo "8 kafka.p12"
  # create PKCS12 store
  openssl pkcs12 -export \
      -in $cert_dir/kafka.crt \
      -inkey $cert_dir/kafka.key \
      -chain \
      -CAfile kafka-certs/ca.pem \
      -name kafka-$no \
      -out $cert_dir/kafka.p12 \
      -password pass:password${no}1
  # output - kafka-1.p12

  echo "9 PKCS12 store"
  # confluent 7
  # we need to create the broker keystore and import the certificate:
  # create PKCS12 keystore
  docker run --rm -v $(pwd):$(pwd) -w $(pwd) openjdk:17 \
      keytool -importkeystore \
      -deststorepass password${no}2 \
      -destkeystore $cert_dir/kafka.keystore.pkcs12 \
      -srckeystore $cert_dir/kafka.p12 \
      -deststoretype PKCS12  \
      -srcstoretype PKCS12 \
      -noprompt \
      -srcstorepass password${no}1
  # .pkcs12 - PKCS12-store


  echo "11 passwords"
  echo "password${no}1" > $cert_dir/kafka_sslkey_creds
  echo "password${no}2" > $cert_dir/kafka_keystore_creds

  echo "12 JKS keystore"
  # convert to JKS keystore
  docker run --rm -v $(pwd):$(pwd) -w $(pwd) openjdk:17 \
      keytool -importkeystore \
      -srckeystore $cert_dir/kafka.p12 \
      -srcstoretype PKCS12 \
      -srcstorepass password${no}1 \
      -destkeystore $cert_dir/kafka.keystore.jks \
      -deststoretype JKS \
      -deststorepass password${no}2


}

broker_cert 1
broker_cert 2
broker_cert 3
broker_cert m1
broker_cert m2
broker_cert m3

echo "password3" > kafka-certs/kafka_truststore_creds

echo "13 JKS truststore"
# convert to JKS truststore
docker run --rm -v $(pwd):$(pwd) -w $(pwd) openjdk:17 \
    keytool -import \
    -file kafka-certs/ca.crt \
    -alias ca \
    -keystore kafka-certs/kafka.truststore.jks \
    -storepass password3 \
    -noprompt \
    -trustcacerts
# output -  kafka.truststore.jks

if [ -d /vagrant/kafka/big-data ]; then
  cp -r kafka-certs /vagrant/kafka/big-data
fi

echo "14 PKCS12 truststore"
docker run --rm -v $(pwd):$(pwd) -w $(pwd) openjdk:17 \
keytool -keystore kafka-certs/kafka.client.truststore.pkcs12 \
    -alias CARoot \
    -import \
    -file kafka-certs/ca.crt \
    -storepass password4  \
    -noprompt \
    -storetype PKCS12
# output - kafka.client.truststore.pkcs12

echo "all certificates has been created successfully"

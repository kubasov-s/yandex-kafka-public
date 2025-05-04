package org.example.ban.product.service;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.example.ban.product.config.ServiceProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ServiceProperties serviceProperties;

    public void handle(List<String> banProductList, List<String> unbanProductList) {
        final Properties properties = getKafkaProperties();

        try (KafkaProducer<String, Boolean> producer = new KafkaProducer<>(properties)) {
            if (banProductList != null) {
                for (String product : banProductList) {
                    banProduct(producer, product, true);
                }
            }
            if (unbanProductList != null) {
                for (String product : unbanProductList) {
                    banProduct(producer, product, false);
                }
            }
        }
    }

    private void banProduct(KafkaProducer<String, Boolean> producer, String product, boolean shouldBan) {
        ProducerRecord<String, Boolean> record = new ProducerRecord<>(serviceProperties.getTopic(), product, shouldBan);
        try {
            producer.send(record).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("error banning/unbanning product {} in kafka topic {}", product, serviceProperties.getTopic(), e);
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private Properties getKafkaProperties() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, serviceProperties.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSchemaSerializer.class.getName());
        properties.put(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2");
        // SASL
        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        properties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, serviceProperties.getSslTruststoreLocation());
        properties.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, serviceProperties.getSslTruststorePassword());
        properties.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        properties.put(SaslConfigs.SASL_JAAS_CONFIG, serviceProperties.getSaslJaasConfig());
        // schema registry
        properties.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, serviceProperties.getSchemaRegistryUrl());
        // register new schema ensuring backward compatibility
        properties.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, true);
        // SSL
        properties.put("schema.registry.ssl.truststore.location", serviceProperties.getSslTruststoreLocation());
        properties.put("schema.registry.ssl.truststore.password", serviceProperties.getSslTruststorePassword());
        return properties;
    }
}

package org.example.shop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.example.shop.config.ServiceProperties;
import org.example.core.model.Product;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProducerService {
    private final ServiceProperties serviceProperties;
    private final ObjectMapper objectMapper;

    public void load(String... filesPaths) {
        final Properties properties = getKafkaProperties();

        try (KafkaProducer<String, Product> producer = new KafkaProducer<>(properties) ) {
            for (var filePath : filesPaths) {
                var file = new File(filePath);
                if (!file.exists()) {
                    log.error("file is not found: {}", filePath);
                    continue;
                }
                if (file.isFile()) {
                    loadFile(file, producer);
                } else if (file.isDirectory()) {
                    loadDirectory(file, producer);
                } else {
                    log.error("file is neither file nor directory: {}", file);
                }
            }
        }
    }

    private void loadFile(File file, KafkaProducer<String, Product> producer) {
        Product product;
        try {
            product = objectMapper.readValue(file, Product.class);
        } catch (IOException e) {
            log.error("error reading product from file {}", file, e);
            return;
        }
        if (product == null) {
            return;
        }
        ProducerRecord<String, Product> record = new ProducerRecord<>(serviceProperties.getTopic(), product.getName(), product);
        try {
            producer.send(record).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("error loading product {} to kafka topic {}", file, serviceProperties.getTopic(), e);
        }
        log.info("loaded file {} to kafka topic {}", file, serviceProperties.getTopic());
    }


    private void loadDirectory(File directory, KafkaProducer<String, Product> producer) {
        if (directory == null) {
            return;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (var file : files) {
                if (file.isFile()) {
                    loadFile(file, producer);
                }
            }
        }
        log.info("loaded files in directory {}", directory);
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

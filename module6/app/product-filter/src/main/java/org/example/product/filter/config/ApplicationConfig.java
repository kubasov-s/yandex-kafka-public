package org.example.product.filter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.streams.serdes.json.KafkaJsonSchemaSerde;
import org.example.core.model.Product;
import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties
public class ApplicationConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public KafkaJsonSchemaSerde<Product> productSerde(ServiceProperties serviceProperties) {
        final KafkaJsonSchemaSerde<Product> jsonSchemaSerde = new KafkaJsonSchemaSerde<>(Product.class);
        Map<String, Object> serdeConfig = new HashMap<>();
        serdeConfig.put(SCHEMA_REGISTRY_URL_CONFIG, serviceProperties.getSchemaRegistryUrl());
        jsonSchemaSerde.configure(serdeConfig, false);
        return jsonSchemaSerde;
    }

    @Bean
    public KafkaJsonSchemaSerde<Boolean> bannedProductSerde(ServiceProperties serviceProperties) {
        final KafkaJsonSchemaSerde<Boolean> jsonSchemaSerde = new KafkaJsonSchemaSerde<>(Boolean.class);
        Map<String, Object> serdeConfig = new HashMap<>();
        serdeConfig.put(SCHEMA_REGISTRY_URL_CONFIG, serviceProperties.getSchemaRegistryUrl());
        jsonSchemaSerde.configure(serdeConfig, false);
        return jsonSchemaSerde;
    }
}

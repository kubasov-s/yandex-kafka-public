package org.example.client.service;

import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.example.client.config.ServiceProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.StreamSupport;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationService {
    private final ServiceProperties serviceProperties;

    public List<String> getRecommendation() {
        final Properties kafkaProperties = getKafkaProperties();
        try (KafkaConsumer<String, ArrayList<String>> consumer = new KafkaConsumer<>(kafkaProperties)) {
            consumer.subscribe(Collections.singletonList(serviceProperties.getRecommendationTopic()));
            log.info("getting recommendations...");
            ConsumerRecords<String, ArrayList<String>> records = consumer.poll(Duration.ofMillis(serviceProperties.getWaitRecommendationMs()));
            log.debug("recommendation count {}", records.count());
            if (records.isEmpty()) {
                return List.of();
            }
            // get the last recommendation
            return StreamSupport.stream(records.spliterator(), false)
                    .reduce((acc, x) -> x)
                    .map(x -> (List<String>)x.value())
                    .orElse(List.of());
        }
    }

    @NotNull
    private Properties getKafkaProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, serviceProperties.getSlaveBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, serviceProperties.getSlaveGroupId());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, serviceProperties.getWaitRecommendationMs());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class.getName());
        // SASL
        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        properties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, serviceProperties.getSslTruststoreLocation());
        properties.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, serviceProperties.getSslTruststorePassword());
        properties.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        properties.put(SaslConfigs.SASL_JAAS_CONFIG, serviceProperties.getSlaveSaslJaasConfig());
        // schema registry
        properties.put("schema.registry.url", serviceProperties.getSchemaRegistryUrl());
        return properties;
    }
}

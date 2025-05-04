package org.example.product.filter.service;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.json.KafkaJsonSchemaSerde;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.example.core.model.Product;
import org.example.product.filter.config.ServiceProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.example.product.filter.config.Const;

import java.time.Duration;
import java.util.Properties;

@Slf4j
@Service
public class FilterService {
    private final ServiceProperties serviceProperties;
    private final KafkaJsonSchemaSerde<Product> productSerde;
    private final KafkaJsonSchemaSerde<Boolean> bannedProductSerde;
    private final KafkaStreams streams;

    public FilterService(ServiceProperties serviceProperties,
                         KafkaJsonSchemaSerde<Product> productSerde,
                         KafkaJsonSchemaSerde<Boolean> bannedProductSerde) {
        this.serviceProperties = serviceProperties;
        this.productSerde = productSerde;
        this.bannedProductSerde = bannedProductSerde;
        this.streams = startStreams();
    }

    private KafkaStreams startStreams() {
        StreamsBuilder builder = new StreamsBuilder();

        /*
        banned products
        key - product name
        value - whether the product should be banned
         */
        KTable<String, Boolean> bannedProductTable = builder.table(
            serviceProperties.getBannedProductTopic(),
            Consumed.with(Serdes.String(), bannedProductSerde),
            Materialized.as(Const.BANNED_PRODUCT_STORE)
        );

        /*
        product stream
        input
        key: product name
        value: product object
         */
        builder.stream(serviceProperties.getInputTopic(), Consumed.with(Serdes.String(), productSerde))
            // логируем исходное сообщение
            .peek((key, message) -> log.info("input: ({}, {})", key, message))
            // применяем фильтр заблокированный товаров
            .leftJoin(bannedProductTable, (productName, product, isBanned) -> {
                if (isBanned != null && isBanned) {
                    log.info("product removed: ({}, {})", productName, product);
                    return null;
                }
                return product;
            })
            .filter((key, product) -> product != null)
            /*
            сохраняем результат в выходной топик
            output
            key: product name
            value: product object
             */
            .to(serviceProperties.getOutputTopic(), Produced.with(Serdes.String(), productSerde));

        // Старт потока
        var streams = new KafkaStreams(builder.build(), getKafkaProperties());
        streams.start();
        return streams;
    }

    @NotNull
    private Properties getKafkaProperties() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, serviceProperties.getApplicationId());
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, serviceProperties.getBootstrapServers());
        // SASL
        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        properties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, serviceProperties.getSslTruststoreLocation());
        properties.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, serviceProperties.getSslTruststorePassword());
        properties.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        properties.put(SaslConfigs.SASL_JAAS_CONFIG, serviceProperties.getSaslJaasConfig());
        // schema registry
        properties.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, serviceProperties.getSchemaRegistryUrl());
        // do not register new schema
        properties.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, false);
        // SSL
        properties.put("schema.registry.ssl.truststore.location", serviceProperties.getSslTruststoreLocation());
        properties.put("schema.registry.ssl.truststore.password", serviceProperties.getSslTruststorePassword());
        return properties;
    }

    @EventListener(ContextClosedEvent.class)
    private void onContextClosedEvent(ContextClosedEvent contextClosedEvent) {
        streams.close(Duration.ofSeconds(10));
    }
}

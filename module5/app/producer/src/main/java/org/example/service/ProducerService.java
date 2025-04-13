package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.example.User;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
public class ProducerService {
    @Value("${service.bootstrap-servers}")
    private String bootstrapServers;
    @Value("${service.schema-registry-url}")
    private String schemaRegistryUrl;
    @Value("${service.topic}")
    private String topic;
    @Value("${service.ssl-truststore-location}")
    private String truststoreLocation;
    @Value("${service.ssl-truststore-password}")
    private String truststorePassword;
    @Value("${service.sasl-jaas-config}")
    private String saslJaasConfig;
    @Autowired
    private ObjectMapper objectMapper;

    public void run() {
        final Properties properties = getKafkaProperties();
        User user = null;

        try (KafkaProducer<String, User> producer = new KafkaProducer<>(properties) ) {
            while(true) {
                user = new org.example.User("Yet another user", LocalDateTime.now().toString());
                ProducerRecord<String, User> record = new ProducerRecord<>(topic, null, user);
                producer.send(record).get();
                log.info("sent message: {}", toStringSafe(user));
                Thread.sleep(3000);
            }
        } catch (ExecutionException e) {
            log.error("failed sending message: {}", user, e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private Properties getKafkaProperties() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSchemaSerializer.class.getName());
        // SASL
        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        properties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreLocation);
        properties.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword);
        properties.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        properties.put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig);
        // schema registry
        properties.put("schema.registry.url", schemaRegistryUrl);
        // register new schema ensuring backward compatibility
        properties.put("auto.register.schemas", true);
        properties.put("basic.auth.credentials.source", "SASL_INHERIT");
        properties.put("schema.registry.ssl.truststore.location", truststoreLocation);
        properties.put("schema.registry.ssl.truststore.password", truststorePassword);
        return properties;
    }

    private <T> String toStringSafe(T payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("failed to serialize payload object {}", payload, e);
            return "";
        }
    }
}

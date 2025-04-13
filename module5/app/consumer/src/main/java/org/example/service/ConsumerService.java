package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.example.User;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.apache.hadoop.fs.FileSystem;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
public class ConsumerService {
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
    @Value("${service.group-id}")
    private String groupId;
    @Value("${service.hdfs-uri}")
    private String hdfsUri;
    @Value("${service.hdfs-user}")
    private String hdfsUser;
    @Autowired
    private ObjectMapper objectMapper;

    public void run() {
        final Properties properties = getKafkaProperties();
        final Configuration conf = getHadoopConfiguration();

        final CountDownLatch latch = new CountDownLatch(1);
        try (KafkaConsumer<String, JsonNode> consumer = new KafkaConsumer<>(properties);
             FileSystem hdfs = FileSystem.get(new URI(hdfsUri), conf, hdfsUser)) {
            consumer.subscribe(Collections.singletonList(topic));

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Caught shutdown signal, closing consumer...");
                consumer.wakeup();
                latch.countDown();
            }));

            while (true) {
                ConsumerRecords<String, JsonNode> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, JsonNode> record : records) {
                    User user = objectMapper.convertValue(record.value(), new TypeReference<User>(){});
                    log.info("received message: {}", record.value());
                    writeToHdfs(hdfs, user);
                }
            }
        } catch (WakeupException e) {
            // Expected during shutdown
        } catch (Exception e) {
            log.error("consumer failed", e);
        } finally {
            latch.countDown();
        }
    }

    private void writeToHdfs(FileSystem hdfs, User user) throws IOException {
        String hdfsFilePath = "/data/message_" + UUID.randomUUID();
        Path path = new Path(hdfsFilePath);
        String value = objectMapper.writeValueAsString(user);

        // Запись файла в HDFS
        try (FSDataOutputStream outputStream = hdfs.create(path, true)) {
            outputStream.writeUTF(value);
        }
        log.info("message saved to hdfs: {}", hdfsFilePath);
    }

    @NotNull
    private Configuration getHadoopConfiguration() {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", hdfsUri);
        conf.set("dfs.client.use.datanode.hostname", "true");
        return conf;
    }

    @NotNull
    private Properties getKafkaProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class.getName());
        // SASL
        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        properties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreLocation);
        properties.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword);
        properties.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        properties.put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig);
        // schema registry
        properties.put("schema.registry.url", schemaRegistryUrl);
        properties.put("basic.auth.credentials.source", "SASL_INHERIT");
        properties.put("schema.registry.ssl.truststore.location", truststoreLocation);
        properties.put("schema.registry.ssl.truststore.password", truststorePassword);
        return properties;
    }
}

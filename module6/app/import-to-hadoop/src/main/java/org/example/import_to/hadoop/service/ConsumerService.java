package org.example.import_to.hadoop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import lombok.RequiredArgsConstructor;
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
import org.example.import_to.hadoop.config.ServiceProperties;
import org.example.core.model.Product;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.apache.hadoop.fs.FileSystem;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerService {
    private final ServiceProperties serviceProperties;
    private final ObjectMapper objectMapper;

    public void run() {
        final Properties kafkaProperties = getKafkaProperties();
        final Configuration hadoopConfiguration = getHadoopConfiguration();

        final CountDownLatch latch = new CountDownLatch(1);
        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(kafkaProperties);
             FileSystem hdfs = FileSystem.get(new URI(serviceProperties.getHdfsUri()), hadoopConfiguration, serviceProperties.getHdfsUser())) {
            consumer.subscribe(List.of(serviceProperties.getProductTopic(), serviceProperties.getRequestTopic()));

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Caught shutdown signal, closing consumer...");
                consumer.wakeup();
                latch.countDown();
            }));

            while (true) {
                ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, Object> record : records) {
                    if (record.topic().equals(serviceProperties.getProductTopic())) {
                        Product product = objectMapper.convertValue(record.value(), new TypeReference<Product>() {
                        });
                        log.info("received product message: {}", record.value());
                        writeProductToHdfs(hdfs, product);
                    }
                    if (record.topic().equals(serviceProperties.getRequestTopic())) {
                        String request = (String)record.value();
                        log.info("received client request message: {}", request);
                        writeRequestToHdfs(hdfs, request);
                    }
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

    private void writeProductToHdfs(FileSystem hdfs, Product product) throws IOException {
        String hdfsFilePath = "/data/product-" + UUID.randomUUID();
        Path path = new Path(hdfsFilePath);
        String value = objectMapper.writeValueAsString(product);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        try (FSDataOutputStream outputStream = hdfs.create(path, true)) {
            outputStream.write(bytes);
        }
        log.info("message saved to hdfs: {}", hdfsFilePath);
    }

    private void writeRequestToHdfs(FileSystem hdfs, String request) throws IOException {
        String hdfsFilePath = "/data/request-" + UUID.randomUUID();
        Path path = new Path(hdfsFilePath);
        byte[] bytes = request.getBytes(StandardCharsets.UTF_8);

        try (FSDataOutputStream outputStream = hdfs.create(path, true)) {
            outputStream.write(bytes);
        }
        log.info("message saved to hdfs: {}", hdfsFilePath);
    }

    @NotNull
    private Configuration getHadoopConfiguration() {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", serviceProperties.getHdfsUri());
        conf.set("dfs.client.use.datanode.hostname", "true");
        return conf;
    }

    @NotNull
    private Properties getKafkaProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, serviceProperties.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, serviceProperties.getGroupId());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class.getName());
        // SASL
        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        properties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, serviceProperties.getSslTruststoreLocation());
        properties.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, serviceProperties.getSslTruststorePassword());
        properties.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        properties.put(SaslConfigs.SASL_JAAS_CONFIG, serviceProperties.getSaslJaasConfig());
        // schema registry
        properties.put("schema.registry.url", serviceProperties.getSchemaRegistryUrl());
        return properties;
    }
}

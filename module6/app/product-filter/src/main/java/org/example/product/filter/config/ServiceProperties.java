package org.example.product.filter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("service")
@Getter
@Setter
public class ServiceProperties {
    private String applicationId;
    private String bootstrapServers;
    private String schemaRegistryUrl;
    private String inputTopic;
    private String outputTopic;
    private String bannedProductTopic;
    private String sslTruststoreLocation;
    private String sslTruststorePassword;
    private String saslJaasConfig;
}

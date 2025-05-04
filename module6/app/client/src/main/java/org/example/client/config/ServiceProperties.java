package org.example.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("service")
@Getter
@Setter
public class ServiceProperties {
    private String slaveBootstrapServers;
    private String masterBootstrapServers;
    private String schemaRegistryUrl;
    private String recommendationTopic;
    private String clientRequestTopic;
    private String sslTruststoreLocation;
    private String sslTruststorePassword;
    private String masterSaslJaasConfig;
    private String slaveSaslJaasConfig;
    private String slaveGroupId;
    private String productFile;
    private int waitRecommendationMs;
}

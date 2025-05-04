package org.example.import_to.hadoop.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("service")
@Getter
@Setter
public class ServiceProperties {
    private String bootstrapServers;
    private String schemaRegistryUrl;
    private String productTopic;
    private String requestTopic;
    private String sslTruststoreLocation;
    private String sslTruststorePassword;
    private String saslJaasConfig;
    private String groupId;
    private String hdfsUri;
    private String hdfsUser;
}

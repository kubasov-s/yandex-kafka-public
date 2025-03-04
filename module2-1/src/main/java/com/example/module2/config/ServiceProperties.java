package com.example.module2.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("service")
@Getter
@Setter
public class ServiceProperties {
    private String boostrapServersConfig;
    private String inputMessageTopic;
    private String outputMessageTopic;
    private String blockedUsersTopic;
    private String blockedWordsTopic;
}

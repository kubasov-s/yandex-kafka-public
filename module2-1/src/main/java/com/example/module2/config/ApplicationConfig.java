package com.example.module2.config;

import com.example.module2.serdes.MessageSerdes;
import com.example.module2.serdes.UserSetSerdes;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ApplicationConfig {
    @Bean
    public ExecutorService serviceExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    public MessageSerdes messageSerdes(ObjectMapper objectMapper) {
        return new MessageSerdes(objectMapper);
    }

    @Bean
    public UserSetSerdes userSetSerdes(ObjectMapper objectMapper) {
        return new UserSetSerdes(objectMapper);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}

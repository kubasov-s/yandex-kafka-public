package org.example;

import org.apache.log4j.BasicConfigurator;
import org.example.service.ConsumerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KafkaConsumerApp implements CommandLineRunner {
    @Autowired
    private ConsumerService consumerService;

    public static void main(String[] args) {
        SpringApplication.run(KafkaConsumerApp.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        BasicConfigurator.configure();
        consumerService.run();
    }
}

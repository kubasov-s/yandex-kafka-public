package org.example;

import org.example.service.ProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KafkaProducerApp implements CommandLineRunner {
    @Autowired
    private ProducerService producerService;

    public static void main(String[] args) {
        SpringApplication.run(KafkaProducerApp.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        producerService.run();
    }
}

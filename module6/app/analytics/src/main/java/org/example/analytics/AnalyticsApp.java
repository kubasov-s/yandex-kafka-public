package org.example.analytics;

import org.apache.log4j.BasicConfigurator;
import org.example.analytics.service.MainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AnalyticsApp implements CommandLineRunner {
    @Autowired
    private MainService mainService;

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApp.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        BasicConfigurator.configure();
        mainService.run();
    }
}

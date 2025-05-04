package org.example.import_to.hadoop;

import org.apache.log4j.BasicConfigurator;
import org.example.import_to.hadoop.service.ConsumerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HadoopExporterApp implements CommandLineRunner {
    @Autowired
    private ConsumerService consumerService;

    public static void main(String[] args) {
        SpringApplication.run(HadoopExporterApp.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        BasicConfigurator.configure();
        consumerService.run();
    }
}

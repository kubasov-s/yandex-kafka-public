package org.example.shop;

import org.example.shop.service.ProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShopApp implements CommandLineRunner {
    @Autowired
    private ProducerService producerService;

    public static void main(String[] args) {
        SpringApplication.run(ShopApp.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            System.out.println("""
            Specify files and directories to load into kafka.
            Each file should contain a single product in JSON format. File name and extension does not matter.
            Directory processing is not recursive.
            
            usage:
            module6-shop <file>... <directory>...
            """);
            return;
        }
        producerService.load(args);
    }
}

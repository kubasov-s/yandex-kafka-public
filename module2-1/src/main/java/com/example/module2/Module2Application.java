package com.example.module2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties
public class Module2Application {

	public static void main(String[] args) {
		SpringApplication.run(Module2Application.class, args);
	}

}

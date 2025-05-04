package org.example.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MainService {
    private final SparkService sparkService;
    private final KafkaService kafkaService;

    public void run() throws URISyntaxException {
        List<String> productRecommendations = sparkService.buildRecommendations();
        if (productRecommendations.isEmpty()) {
            log.info("No recommendations");
            return;
        }
        log.info("Generated product recommendations: {}", productRecommendations);
        kafkaService.send(productRecommendations);
    }
}

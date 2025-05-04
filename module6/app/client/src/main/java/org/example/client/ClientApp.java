package org.example.client;

import lombok.extern.slf4j.Slf4j;
import org.example.client.service.ProductSearcher;
import org.example.client.service.RecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class ClientApp implements ApplicationRunner {
    @Autowired
    private ProductSearcher productSearcher;
    @Autowired
    private RecommendationService recommendationService;

    public static void main(String[] args) {
        SpringApplication.run(ClientApp.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var productList = args.getOptionValues("product");
        if (productList != null && !productList.isEmpty()) {
            productList.forEach(product -> {
                System.out.format("looking up product: %s\n", product);
                var results = productSearcher.find(product);
                System.out.format("found %d results\n", results.size());
                results.forEach(result-> System.out.format("> %s\n", result));
            });
            return;
        }
        if (args.containsOption("recommend")) {
            var recommendProductList = recommendationService.getRecommendation();
            if (recommendProductList.isEmpty()) {
                System.out.println("No recommendations have been found");
            } else {
                System.out.println("recommended products:");
                recommendProductList.forEach(result -> System.out.format("> %s\n", result));
            }
            return;
        }
        usage();
    }

    private static void usage() {
        System.out.println("""
            usage:
            client OPTIONS
            
            Options:
            --product=<product name> - look up product
            --recommend - get recommended products
            """);
    }
}

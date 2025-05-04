package org.example.ban.product;

import org.example.ban.product.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BanProductApp implements ApplicationRunner {
    @Autowired
    private ProductService productService;

    public static void main(String[] args) {
        SpringApplication.run(BanProductApp.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var banProducts = args.getOptionValues("ban");
        var unbanProducts = args.getOptionValues("unban");
        if ((banProducts != null && !banProducts.isEmpty()) || (unbanProducts != null && !unbanProducts.isEmpty())) {
            productService.handle(banProducts, unbanProducts);
        } else {
            usage();
        }
    }

    private void usage() {
        System.out.println("""
            ban-product OPTIONS
            Options
            --ban=<product name> - ban product
            --unban=<product name> - unban product
            """);
    }
}

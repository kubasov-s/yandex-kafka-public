package org.example.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.example.analytics.config.ServiceProperties;
import org.springframework.stereotype.Service;
import scala.Tuple2;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SparkService {
    private final ServiceProperties serviceProperties;
    private final ObjectMapper objectMapper;

    public List<String> buildRecommendations() throws URISyntaxException {
        SparkConf sparkConf = new SparkConf()
                .setAppName("KafkaHdfsSparkConsumer")
                .setMaster("local[*]")
                .set("spark.hadoop.dfs.client.use.datanode.hostname", "true");
        try (JavaSparkContext context = new JavaSparkContext(sparkConf)) {
            // all products
            var products = getProductProperties(context);
            // all client requests (product names)
            var clientProducts = getClientRequests(context);
            var productsPairByName = products.mapToPair(p -> new Tuple2<>(p.name, p));
            var clientProductsPair = clientProducts.mapToPair(x -> new Tuple2<>(x, x));
            // product categories where client looked up goods
            var clientCategoriesPair = productsPairByName.join(clientProductsPair)
                    .map(x -> x._2._1.category)
                    .distinct()
                    .mapToPair(x -> new Tuple2<>(x, x));
            var productsPairByCategory = products.mapToPair(x -> new Tuple2<>(x.category, x));
            var recommendedProductsNames = productsPairByCategory.join(clientCategoriesPair)
                    .map(x -> x._2._1.name)
                    .distinct().collect();
            if (recommendedProductsNames.isEmpty()) {
                log.warn("No product matching client queries");
                return List.of();
            }
            if (recommendedProductsNames.size() <= serviceProperties.getMaxRecommendations()) {
                return recommendedProductsNames;
            }
            Collections.shuffle(recommendedProductsNames);
            return recommendedProductsNames.subList(0, serviceProperties.getMaxRecommendations());
        }
    }

    private JavaRDD<String> getClientRequests(JavaSparkContext sc) throws URISyntaxException {
        String requestUrl = new URI(serviceProperties.getHdfsUri()).resolve("/data/request-*").toString();
        JavaRDD<String> requestRdd = sc.wholeTextFiles(requestUrl).map(Tuple2::_2);
        return requestRdd.distinct();
    }

    private JavaRDD<ProductProperties> getProductProperties(JavaSparkContext sc) throws URISyntaxException {
        String productUrl = new URI(serviceProperties.getHdfsUri()).resolve("/data/product-*").toString();
        JavaRDD<String> productJsonRdd = sc.wholeTextFiles(productUrl).map(Tuple2::_2);
        Broadcast<ObjectMapper> objectMapperBroadcast = sc.broadcast(objectMapper);
        return productJsonRdd.map(s -> {
            var tree = objectMapperBroadcast.value().readTree(s);
            var name = tree.get("name").asText();
            var category = tree.get("category").asText();
            return new ProductProperties(name, category);
        }).distinct();
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode
    private static class ProductProperties implements Serializable {
        private String name;
        private String category;

        @Override
        public String toString() {
            return String.format("{%s, %s}", name, category);
        }
    }
}

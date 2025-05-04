package org.example.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Product {
    @JsonProperty(required = true)
    private String productId;
    @JsonProperty(required = true)
    private String name;
    @JsonProperty
    private String description;
    @JsonProperty
    private Price price;
    @JsonProperty
    private String category;
    @JsonProperty
    private String brand;
    @JsonProperty
    private Stock stock;
    @JsonProperty
    private String sku;
    @JsonProperty
    private List<String> tags;
    @JsonProperty
    private List<Image> images;
    @JsonProperty
    private Map<String, String> specifications;
    @JsonProperty
    private String createdAt;
    @JsonProperty
    private String updatedAt;
    @JsonProperty
    private String index;
    @JsonProperty
    private String storeId;

    public record Price(BigDecimal amount, String currency) {}
    public record Stock(int available, int reserved) {}
    public record Image(URL url, String alt) {}
}

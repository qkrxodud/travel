package com.kobi.territory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TerritoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(TerritoryApplication.class, args);
    }
}

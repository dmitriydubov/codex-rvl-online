package ru.projectx.datareference;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DataReferenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataReferenceApplication.class, args);
    }
}

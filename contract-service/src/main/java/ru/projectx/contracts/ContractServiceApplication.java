package ru.projectx.contracts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Сервис Договоров: по заявкам сервиса Заявок выпускает договоры КМ (на сумму доплаты застрахованного) и запрашивает
 * у 1С:УСК полисы ЛМ родственникам без доплаты. Он же бэкенд личного кабинета застрахованного.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ContractServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContractServiceApplication.class, args);
    }
}

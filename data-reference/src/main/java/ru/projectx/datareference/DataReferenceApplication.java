package ru.projectx.datareference;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Сервис справочных данных: принимает справочники и полисы из 1С:УСК через её RabbitMQ, хранит сырые сообщения,
 * по расписанию раскладывает их по таблицам PostgreSQL и отдаёт по REST сервисам Заявок и Договоров.
 * Расписания включает {@link ru.projectx.datareference.config.SchedulingConfig}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DataReferenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataReferenceApplication.class, args);
    }
}

package ru.projectx.datareference;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Сервис справочных данных {@code data-reference}: принимает справочники и полисы из учётной системы
 * 1С:УСК и отдаёт их по REST сервисам Заявок и Договоров.
 *
 * <p>Единственный источник данных — 1С:УСК. Она публикует изменения в свой RabbitMQ, сервис их
 * вычитывает, складывает сырые сообщения у себя и по расписанию раскладывает по таблицам PostgreSQL:
 * санатории, договоры, программы, услуги, цены и полисы застрахованных. Расписания включает
 * {@link ru.projectx.datareference.config.SchedulingConfig}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DataReferenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataReferenceApplication.class, args);
    }
}

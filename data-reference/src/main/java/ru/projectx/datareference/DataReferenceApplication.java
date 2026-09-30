package ru.projectx.datareference;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code data-reference} — сервис справочных данных контура РВЛ ОНЛАЙН: справочники
 * и полисы из 1С:УСК, наружу — REST для сервисов Заявок и Договоров.
 *
 * <p>Единственный источник данных — учётная система 1С:УСК. Она публикует изменения
 * в свой RabbitMQ, сервис их вычитывает, складывает сырьё у себя и раскладывает
 * по таблицам PostgreSQL: справочники санаториев, договоров, программ, услуг и цен,
 * а также полисы застрахованных. Разбор потоков и порядок разработки — в {@code CLAUDE.md}.
 *
 * <p>Приложение долгоживущее: его держит шедулер, разбирающий накопленное сырьё.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DataReferenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataReferenceApplication.class, args);
    }
}

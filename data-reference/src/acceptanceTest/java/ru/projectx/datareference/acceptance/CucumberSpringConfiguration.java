package ru.projectx.datareference.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import ru.projectx.datareference.config.OneCQueueProperties;

/**
 * Один контекст Spring на все сценарии приёмки: настоящие PostgreSQL и RabbitMQ тех же версий, что в контуре. Своя
 * обвязка, а не классы из src/test, потому что приёмка в контур не переносится. Расписания выключены, чтобы проходы не
 * меняли статусы посреди сценария; TLS и слушатели заданы явно, чтобы не зависеть от окружения запускающего.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "projectx.scheduling.enabled=false",
        "spring.rabbitmq.ssl.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=true"})
@Import(CucumberSpringConfiguration.Containers.class)
public class CucumberSpringConfiguration {

    /** Контейнеры приёмки: адрес и учётные данные подставляет {@link ServiceConnection}. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {

        private static final String POSTGRES_IMAGE = "postgres:16.10-alpine";
        /** Та же версия, что у брокера 1С:УСК на контуре. */
        private static final String RABBITMQ_IMAGE = "rabbitmq:4.1.2-alpine";
        private static final String DATA_SOURCE_BEAN = "dataSource";
        /** Свойство с «сегодня» стенда; при запуске — переменная окружения {@code ACCEPTANCE_TODAY}. */
        static final String TODAY = "acceptance.today";

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(POSTGRES_IMAGE);
        }

        @Bean
        @ServiceConnection
        RabbitMQContainer rabbitContainer() {
            return new RabbitMQContainer(RABBITMQ_IMAGE);
        }

        /**
         * Часы сервиса, которыми управляют шаги сценария; {@code @Primary} побеждает часы из конфигурации сервиса.
         * Со свойством {@value #TODAY} идущие часы показывают этот день по Москве: так приёмка проверяется на любую дату.
         */
        @Bean
        @Primary
        StandClock standClock(Environment environment) {
            return Optional.ofNullable(environment.getProperty(TODAY))
                    .map(Containers::today)
                    .map(StandClock::runningOn)
                    .orElseGet(StandClock::new);
        }

        private static LocalDate today(String value) {
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException e) {
                throw new IllegalStateException(
                        TODAY + " (ACCEPTANCE_TODAY) ждёт дату yyyy-MM-dd, а задано: '" + value + "'", e);
            }
        }

        /** Справки об очередях для шагов сценария; очередей сам не объявляет — бинов очередей в контексте нет. */
        @Bean
        RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
            return new RabbitAdmin(connectionFactory);
        }

        /**
         * Очереди контура объявляет стенд, как на контуре их заводит 1С:УСК, и раньше старта потребителей: забытая
         * очередь уронила бы контекст, а не прошла молча.
         */
        @Bean
        OneCStandQueues oneCStandQueues(ConnectionFactory connectionFactory, OneCQueueProperties queues) {
            OneCStandQueues standQueues = new OneCStandQueues(connectionFactory);
            standQueues.declareAll(List.of(
                    queues.policies(), queues.insuranceContracts(), queues.contragents(), queues.mkb10(),
                    queues.lpuContracts(), queues.programs(), queues.appealTypes(), queues.lpuContacts(),
                    queues.priceLists(), queues.priceListsFinal(), queues.contragentServices()));
            return standQueues;
        }

        /**
         * Оборачивает пул сервиса переключателем до того, как его получат JPA и остальные: «база недоступна» в сценарии
         * значит отказ соединения у всех, кто ходит через пул, а саму общую базу остановить нельзя. Статический, чтобы
         * Spring создал обработчик раньше остальных бинов.
         */
        @Bean
        static BeanPostProcessor switchableDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof DataSource pool && DATA_SOURCE_BEAN.equals(beanName)) {
                        return new SwitchableDataSource(pool);
                    }
                    return bean;
                }
            };
        }
    }
}

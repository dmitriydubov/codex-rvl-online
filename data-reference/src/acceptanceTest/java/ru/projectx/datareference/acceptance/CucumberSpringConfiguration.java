package ru.projectx.datareference.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import ru.projectx.datareference.config.OneCQueueProperties;

/**
 * Один контекст Spring на все сценарии приёмки (cucumber-spring). Step-определения лежат
 * в этом же пакете ({@code acceptance.glue} в gauntlet/config/data-reference/gauntlet.properties).
 *
 * <p>Приёмочные тесты живут только в GitHub и облаке Claude Code: в рабочий контур
 * каталог src/acceptanceTest не переносится, и ни продакшен-код, ни unit-тесты от него
 * не зависят. Поэтому здесь своя обвязка, а не классы из src/test.
 *
 * <p>Стенд — настоящий PostgreSQL той же версии, что в тестах и в контуре, и настоящий RabbitMQ той же
 * версии, что у брокера 1С:УСК: сценарии приёма публикуют сообщения в очереди с именами контура, и принимает
 * их штатный слушатель. Очереди объявляет стенд, а не сервис (на контуре их заводит 1С:УСК), и объявляет
 * до старта потребителей: с {@code missing-queues-fatal} потребитель без очереди роняет контекст. Объявляет
 * их {@link OneCStandQueues} прямо в канал, без бинов очередей, чтобы потребитель сервиса, как и на контуре,
 * не объявлял пропавшую очередь сам. TLS
 * выключен и слушатели включены явно, иначе они брались бы из переменных окружения запускающего.
 * Расписания выключены ({@code projectx.scheduling.enabled=false}), чтобы проходы не меняли статусы посреди
 * сценария; проходы разбора, стирания и проверки базы вызывают шаги явно.
 *
 * <p>Недоступность базы сценарии изображают переключателем источника соединений
 * ({@link SwitchableDataSource}): остановить саму базу нельзя, она одна на все сценарии.
 *
 * <p>Контекст и контейнеры общие на все сценарии, поэтому перед каждым сценарием база
 * очищается ({@link DatabaseCleanupHook}), а состояние сценария живёт в бинах
 * {@code @ScenarioScope} ({@link ContractsService}, {@link OneCInbox}, {@link OneCBroker}).
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

        /** Справки об очередях для шагов сценария; очередей сам не объявляет — бинов очередей в контексте нет. */
        @Bean
        RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
            return new RabbitAdmin(connectionFactory);
        }

        /**
         * Все одиннадцать очередей контура объявлены при создании бина — раньше, чем потребители запустятся: забытая
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
         * Оборачивает пул соединений сервиса переключателем до того, как его получат JPA и остальные: так
         * «база недоступна» в сценарии — это отказ на каждом запросе соединения у всех, кто ходит в базу через
         * пул. Соединение миграций Liquibase отдельное и не оборачивается. Статический, чтобы Spring создал
         * обработчик раньше остальных бинов.
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

package ru.projectx.datareference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Контекст поднимается, и Liquibase накатывает схему. Так ловятся поломки, которые иначе видны только
 * на стенде: несогласованные бины, невалидная конфигурация, неприменимый changeSet.
 */
@IntegrationTest
class DataReferenceApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        // Достаточно самого факта старта: падение произойдёт до тела теста.
    }

    /**
     * Свойство {@code auto-startup=false} слушателей не останавливает: при перезапуске кэшированного контекста
     * они поднимались, шли на брокер по умолчанию и роняли чужой тестовый класс. Поэтому без брокера в контексте
     * нет фабрики соединений, и стартовать нечему.
     */
    @Test
    void contextWithoutBroker_hasNoAmqpMachinery() {
        assertThat(context.getBeanNamesForType(ConnectionFactory.class)).isEmpty();
        assertThat(context.getBeanNamesForType(RabbitListenerEndpointRegistry.class)).isEmpty();
    }

    /**
     * Порт 8081 открывает образ и публикует стенд, который переменных окружения сервиса не читает. В остальных
     * тестах на случайных портах умолчание подменено, а MANAGEMENT_PORT там, где идут тесты, не задают.
     */
    @Test
    void managementPort_defaultsTo8081() {
        assertThat(context.getEnvironment().getProperty("management.server.port", Integer.class)).isEqualTo(8081);
    }
}

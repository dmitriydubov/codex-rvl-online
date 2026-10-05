package ru.projectx.datareference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Контекст поднимается и Liquibase накатывает схему.
 *
 * <p>Тест выглядит бессодержательным, но ловит ровно тот класс поломок, который иначе
 * обнаруживается только на стенде: несогласованные бины, невалидная конфигурация,
 * changeSet, который не применяется.
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
     * В контексте без брокера машинерии AMQP быть не должно — и проверяется это здесь,
     * а не читается в аннотации, потому что цена ошибки высокая и отложенная.
     *
     * <p>Свойством {@code auto-startup=false} слушателей не остановить: реестр стартует
     * контейнер при {@code contextRefreshed || isAutoStartup()}, а флаг взводится сразу
     * после первого refresh. Spring перезапускает кэшированные контексты между тестовыми
     * классами, и на перезапуске слушатели поднимались вопреки свойству — шли на брокер
     * по умолчанию и роняли чужой тестовый класс сообщением про несуществующую очередь.
     * Нет фабрики соединений — нечему стартовать ни при каком перезапуске.
     */
    @Test
    void contextWithoutBroker_hasNoAmqpMachinery() {
        assertThat(context.getBeanNamesForType(ConnectionFactory.class)).isEmpty();
        assertThat(context.getBeanNamesForType(RabbitListenerEndpointRegistry.class)).isEmpty();
    }
}

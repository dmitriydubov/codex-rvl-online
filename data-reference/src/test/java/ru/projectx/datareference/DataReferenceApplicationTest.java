package ru.projectx.datareference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class DataReferenceApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
    }

    @Test
    void contextWithoutBroker_hasNoAmqpMachinery() {
        assertThat(context.getBeanNamesForType(ConnectionFactory.class)).isEmpty();
        assertThat(context.getBeanNamesForType(RabbitListenerEndpointRegistry.class)).isEmpty();
    }

    @Test
    void managementPort_defaultsTo8081() {
        assertThat(context.getEnvironment().getProperty("management.server.port", Integer.class)).isEqualTo(8081);
    }
}

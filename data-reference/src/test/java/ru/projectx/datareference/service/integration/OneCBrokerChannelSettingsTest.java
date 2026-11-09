package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.ConnectionFactory;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.support.AmqpIntegrationTest;

@AmqpIntegrationTest
class OneCBrokerChannelSettingsTest {

    private static final Duration DEFAULT_REPLY_LIMIT = Duration.ofSeconds(30);

    @Autowired
    private CachingConnectionFactory connectionFactory;

    @Test
    void brokerClient_waitsForAChannelReplyThirtySecondsByDefault() {
        assertThat(rabbitClient().getChannelRpcTimeout()).isEqualTo(DEFAULT_REPLY_LIMIT.toMillis());
    }

    @Test
    void brokerClient_ignoresRepliesOfAnotherCommandType() {
        assertThat(rabbitClient().isChannelShouldCheckRpcResponseType()).isTrue();
    }

    private ConnectionFactory rabbitClient() {
        return connectionFactory.getRabbitConnectionFactory();
    }
}

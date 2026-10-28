package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Test;

class OneCBrokerChannelsConfigurationTest {

    @Test
    void brokerChannels_ignoreRepliesOfAnotherCommandType() {
        ConnectionFactory factory = new ConnectionFactory();

        new OneCBrokerChannelsConfiguration().replyTypeCheck().customize(factory);

        assertThat(factory.isChannelShouldCheckRpcResponseType()).isTrue();
    }
}

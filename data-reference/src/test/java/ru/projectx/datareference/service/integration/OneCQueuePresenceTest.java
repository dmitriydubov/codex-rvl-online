package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class OneCQueuePresenceTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_policies";
    private static final int NOT_FOUND = 404;
    private static final int ACCESS_REFUSED = 403;
    private static final int CONNECTION_FORCED = 320;
    private static final int QUEUE_CLASS = 50;
    private static final int BASIC_CLASS = 60;
    private static final int DECLARE_METHOD = 10;
    private static final int CONSUME_METHOD = 20;

    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final Channel channel = mock(Channel.class);

    @Test
    void passiveDeclarationSucceeds_queueIsPresent() throws IOException {
        doAnswer(invocation -> invocation.getArgument(0, ChannelCallback.class).doInRabbit(channel))
                .when(template).execute(any());

        assertThat(presence().of(QUEUE)).isEqualTo(OneCQueuePresence.OnBroker.PRESENT);
        verify(channel).queueDeclarePassive(QUEUE);
    }

    @Test
    void brokerClosesTheChannelWithNotFoundOnDeclare_queueIsAbsent() {
        doThrow(new AmqpIOException(new IOException(channelClose(NOT_FOUND, QUEUE_CLASS, DECLARE_METHOD))))
                .when(template).execute(any());

        assertThat(presence().of(QUEUE)).isEqualTo(OneCQueuePresence.OnBroker.ABSENT);
    }

    @Test
    void brokerClosesTheChannelWithAccessRefused_queueIsRefused() {
        doThrow(new AmqpIOException(new IOException(channelClose(ACCESS_REFUSED, QUEUE_CLASS, DECLARE_METHOD))))
                .when(template).execute(any());

        assertThat(presence().of(QUEUE)).isEqualTo(OneCQueuePresence.OnBroker.REFUSED);
    }

    @Test
    void notFoundOnAnotherOperation_isARefusalNotAnAbsence() {
        doThrow(new AmqpIOException(new IOException(channelClose(NOT_FOUND, BASIC_CLASS, CONSUME_METHOD))))
                .when(template).execute(any());

        assertThat(presence().of(QUEUE)).isEqualTo(OneCQueuePresence.OnBroker.REFUSED);
    }

    @Test
    void connectionClosedByBroker_isUnreachable() {
        doThrow(new AmqpConnectException(connectionClose(CONNECTION_FORCED))).when(template).execute(any());

        assertThat(presence().of(QUEUE)).isEqualTo(OneCQueuePresence.OnBroker.UNREACHABLE);
    }

    @Test
    void connectionRefused_isUnreachable() {
        doThrow(new AmqpConnectException(new ConnectException("refused"))).when(template).execute(any());

        assertThat(presence().of(QUEUE)).isEqualTo(OneCQueuePresence.OnBroker.UNREACHABLE);
    }

    @Test
    void templateAnswersNothing_isUnreachable() {
        doAnswer(invocation -> null).when(template).execute(any());

        assertThat(presence().of(QUEUE)).isEqualTo(OneCQueuePresence.OnBroker.UNREACHABLE);
    }

    @Test
    void withoutBroker_isUnreachable() {
        OneCQueuePresence presence = new OneCQueuePresence(
                new StaticListableBeanFactory().getBeanProvider(RabbitTemplate.class));

        assertThat(presence.of(QUEUE)).isEqualTo(OneCQueuePresence.OnBroker.UNREACHABLE);
    }

    private OneCQueuePresence presence() {
        ObjectProvider<RabbitTemplate> provider =
                new StaticListableBeanFactory(Map.of("rabbitTemplate", template)).getBeanProvider(RabbitTemplate.class);
        return new OneCQueuePresence(provider);
    }

    private static ShutdownSignalException channelClose(int replyCode, int classId, int methodId) {
        AMQP.Channel.Close reason = new AMQP.Channel.Close.Builder()
                .replyCode(replyCode)
                .replyText("reply " + replyCode)
                .classId(classId)
                .methodId(methodId)
                .build();
        return new ShutdownSignalException(false, false, reason, null);
    }

    private static ShutdownSignalException connectionClose(int replyCode) {
        AMQP.Connection.Close reason = new AMQP.Connection.Close.Builder()
                .replyCode(replyCode)
                .replyText("reply " + replyCode)
                .build();
        return new ShutdownSignalException(true, false, reason, null);
    }
}

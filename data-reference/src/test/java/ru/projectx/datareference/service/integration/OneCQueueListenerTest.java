package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.CannotAcquireLockException;
import ru.projectx.datareference.config.IngestProperties;
import ru.projectx.datareference.config.JacksonConfig;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.onec.OneCIngestService;

/**
 * Что слушатель отвечает брокеру — единственное его решение, и проверяется оно здесь.
 * Настоящий брокер для этого не нужен и мешал бы: «подтвердил» и «вернул в очередь»
 * различаются вызовом канала, а не наблюдаемым состоянием.
 *
 * <p>Разбор конверта — настоящий: он чистая функция, и подменять его значило бы
 * проверять слушатель на несуществующем поведении.
 */
@ExtendWith(MockitoExtension.class)
class OneCQueueListenerTest {

    private static final long DELIVERY_TAG = 42L;
    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final UUID MESSAGE_ID = UUID.fromString("9f1c0f2e-0000-4000-8000-00000000000a");

    @Mock
    private OneCIngestService ingestService;

    @Mock
    private Channel channel;

    /**
     * Пауза перед возвратом — почти нулевая: проверяется факт возврата, а не длина
     * ожидания, и настоящие пять секунд здесь были бы просто пятью секундами прогона.
     */
    private OneCQueueListener listener() {
        return new OneCQueueListener(
                new OneCEnvelopeParser(new JacksonConfig().objectMapper()),
                ingestService,
                new IngestProperties(Duration.ofMillis(1)));
    }

    @Test
    void storedMessage_isAcknowledged() throws IOException {
        when(ingestService.receive(eq(MESSAGE_ID), eq(OneCStream.MKB10), eq(QUEUE), any())).thenReturn(true);

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    /** Дубль — не ошибка: сырьё уже лежит, брокеру достаточно подтверждения. */
    @Test
    void duplicateMessage_isAcknowledged() throws IOException {
        when(ingestService.receive(eq(MESSAGE_ID), eq(OneCStream.MKB10), eq(QUEUE), any())).thenReturn(false);

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    /** Сырьё сохраняется целиком и как есть: разбирать его будет шедулер, а не слушатель. */
    @Test
    void storedMessage_keepsRawBody() throws IOException {
        String body = envelope(MESSAGE_ID);

        listener().receive(OneCStream.MKB10, delivery(body), channel);

        verify(ingestService).receive(MESSAGE_ID, OneCStream.MKB10, QUEUE, body);
    }

    /**
     * Недоступная база — не повод терять сообщение: оно возвращается в очередь,
     * и брокер отдаст его снова.
     */
    @Test
    void storageFailure_returnsMessageToQueue() throws IOException {
        when(ingestService.receive(any(), any(), any(), any()))
                .thenThrow(new CannotAcquireLockException("база недоступна"));

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    /**
     * Без {@code message-id} сообщение принять нечем — оно подтверждается и теряется.
     * Возврат в очередь дал бы бесконечную передоставку одного и того же.
     */
    @Test
    void unreadableEnvelope_isAcknowledgedAndDropped() throws IOException {
        listener().receive(OneCStream.MKB10, delivery("{\"data\":{},\"metadata\":{}}"), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verifyNoInteractions(ingestService);
    }

    /**
     * У прайсов две очереди на один поток: пакеты строк и финальные сообщения
     * со счётчиками. Различает их только имя очереди, и оно обязано доехать до сырья —
     * иначе сборка передачи (ТЗ-9) не отличит одно от другого.
     */
    @Test
    void priceListFinal_isStoredWithItsOwnQueue() throws IOException {
        String queue = "1c-usk_to_rvl-online_pricelists-contragents-changes-final";

        listener().receive(OneCStream.PRICE_LISTS, delivery(queue, envelope(MESSAGE_ID)), channel);

        verify(ingestService).receive(eq(MESSAGE_ID), eq(OneCStream.PRICE_LISTS), eq(queue), any());
    }

    /**
     * Пауза перед возвратом держится до {@code nack}, иначе недоступная база превращается
     * в горячий цикл передоставки. Проверяется нижняя граница: короче сон не бывает.
     */
    @Test
    void storageFailure_waitsBackoffBeforeReturningMessage() throws IOException {
        Duration backoff = Duration.ofMillis(50);
        when(ingestService.receive(any(), any(), any(), any()))
                .thenThrow(new CannotAcquireLockException("база недоступна"));
        OneCQueueListener listener = new OneCQueueListener(
                new OneCEnvelopeParser(new JacksonConfig().objectMapper()), ingestService, new IngestProperties(backoff));

        long started = System.nanoTime();
        listener.receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(backoff);
        verify(channel).basicNack(DELIVERY_TAG, false, true);
    }

    /**
     * Прерывание во время паузы — остановка контейнера: сообщение возвращается сразу,
     * а флаг прерывания остаётся поднятым, чтобы контейнер увидел остановку.
     */
    @Test
    void interruptedPause_returnsMessageAndKeepsInterruptFlag() throws IOException {
        when(ingestService.receive(any(), any(), any(), any()))
                .thenThrow(new CannotAcquireLockException("база недоступна"));
        Thread.currentThread().interrupt();

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        assertThat(Thread.interrupted()).isTrue();
        verify(channel).basicNack(DELIVERY_TAG, false, true);
    }

    private static String envelope(UUID messageId) {
        return """
                {"data":{"guid":"1a2b3c4d-0000-4000-8000-000000000001"},\
                "metadata":{"message-id":"%s","producer":"1c-usk"}}"""
                .formatted(messageId);
    }

    private static Message delivery(String body) {
        return delivery(QUEUE, body);
    }

    private static Message delivery(String queue, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        properties.setConsumerQueue(queue);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}

package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.TransactionSystemException;
import ru.projectx.datareference.config.IngestProperties;
import ru.projectx.datareference.config.JacksonConfig;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.onec.OneCIngestService;

/**
 * Защищает единственное решение слушателя очередей 1С:УСК: что ответить брокеру после приёма
 * сообщения.
 *
 * <p>Настоящий брокер для этого не нужен и мешал бы: «подтвердил» и «вернул в очередь» различаются
 * вызовом канала, а не наблюдаемым состоянием. Разбор конверта здесь настоящий, потому что это чистая
 * функция, и подменять её значило бы проверять слушатель на несуществующем поведении.
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
     * Пауза перед возвратом почти нулевая, потому что проверяется факт возврата, а не длина ожидания.
     * Настоящие пять секунд здесь были бы просто пятью секундами прогона.
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

    /** Дубль не считается ошибкой: сырьё уже лежит, и брокеру достаточно подтверждения. */
    @Test
    void duplicateMessage_isAcknowledged() throws IOException {
        when(ingestService.receive(eq(MESSAGE_ID), eq(OneCStream.MKB10), eq(QUEUE), any())).thenReturn(false);

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    /** Сырьё сохраняется целиком и как есть, потому что разбирает его планировщик, а не слушатель. */
    @Test
    void storedMessage_keepsRawBody() throws IOException {
        String body = envelope(MESSAGE_ID);

        listener().receive(OneCStream.MKB10, delivery(body), channel);

        verify(ingestService).receive(MESSAGE_ID, OneCStream.MKB10, QUEUE, body);
    }

    /**
     * Недоступная база не повод терять сообщение. Оно возвращается в очередь, и брокер отдаст его
     * снова.
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
     * Без {@code message-id} сообщение принять нечем, поэтому оно подтверждается и теряется. Возврат
     * в очередь дал бы бесконечную передоставку одного и того же.
     */
    @Test
    void unreadableEnvelope_isAcknowledgedAndDropped() throws IOException {
        listener().receive(OneCStream.MKB10, delivery("{\"data\":{},\"metadata\":{}}"), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verifyNoInteractions(ingestService);
    }

    /**
     * Сообщение с {@code message-id} не в канонической записи неопознаваемо. Оно подтверждается и
     * теряется, как нечитаемый конверт, а не ложится в сырьё под GUID
     * {@code 00000001-0002-0003-0004-000000000005}, который нестрогий разбор вычитал бы из
     * {@code 1-2-3-4-5}.
     */
    @Test
    void nonCanonicalMessageId_isAcknowledgedAndDropped() throws IOException {
        listener().receive(OneCStream.MKB10, delivery(envelope("1-2-3-4-5")), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verifyNoInteractions(ingestService);
    }

    /**
     * Нулевой {@code message-id} значит, что идентификатора нет, и такое сообщение тоже подтверждается
     * и теряется. Сохрани слушатель его под нулевым ключом, каждое следующее такое сообщение молча
     * считалось бы дублем первого.
     */
    @Test
    void zeroMessageId_isAcknowledgedAndDropped() throws IOException {
        listener().receive(OneCStream.MKB10, delivery(envelope("00000000-0000-0000-0000-000000000000")), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verifyNoInteractions(ingestService);
    }

    /**
     * У прайсов две очереди на один поток: пакеты строк и финальные сообщения со счётчиками. Различает
     * их только имя очереди, и оно обязано дойти до сырья. Иначе сборка передачи прайса не отличит одно
     * от другого.
     */
    @Test
    void priceListFinal_isStoredWithItsOwnQueue() throws IOException {
        String queue = "1c-usk_to_rvl-online_pricelists-contragents-changes-final";

        listener().receive(OneCStream.PRICE_LISTS, delivery(queue, envelope(MESSAGE_ID)), channel);

        verify(ingestService).receive(eq(MESSAGE_ID), eq(OneCStream.PRICE_LISTS), eq(queue), any());
    }

    /**
     * Подтверждение уходит только после того, как приём зафиксирован. Подтверждённое, но не
     * сохранённое сообщение брокер больше не отдаст, и оно будет потеряно.
     */
    @Test
    void storedMessage_isAcknowledgedOnlyAfterItIsStored() throws IOException {
        when(ingestService.receive(eq(MESSAGE_ID), eq(OneCStream.MKB10), eq(QUEUE), any())).thenReturn(true);

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        InOrder storageThenBroker = inOrder(ingestService, channel);
        storageThenBroker.verify(ingestService).receive(eq(MESSAGE_ID), eq(OneCStream.MKB10), eq(QUEUE), any());
        storageThenBroker.verify(channel).basicAck(DELIVERY_TAG, false);
    }

    /**
     * Пауза стоит <b>между</b> отказом хранения и возвратом, а не после возврата: возврат,
     * отправленный сразу, брокер передоставит немедленно, и пауза уже ничего не сдержит.
     * Поэтому замеряется промежуток от самого отказа до самого {@code nack}.
     */
    @Test
    void storageFailure_returnsMessageOnlyAfterThePause() throws IOException {
        Duration backoff = Duration.ofMillis(50);
        AtomicLong failedAt = new AtomicLong();
        AtomicLong returnedAt = new AtomicLong();
        when(ingestService.receive(any(), any(), any(), any())).thenAnswer(invocation -> {
            failedAt.set(System.nanoTime());
            throw new CannotAcquireLockException("база недоступна");
        });
        doAnswer(invocation -> {
            returnedAt.set(System.nanoTime());
            return null;
        }).when(channel).basicNack(DELIVERY_TAG, false, true);

        listener(backoff).receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        assertThat(returnedAt.get()).as("сообщение возвращено в очередь").isNotZero();
        assertThat(Duration.ofNanos(returnedAt.get() - failedAt.get())).isGreaterThanOrEqualTo(backoff);
    }

    /**
     * «Сразу» значит «не досиживая паузу». Прерывание означает остановку контейнера, и полная пауза на
     * каждом сообщении задержала бы её. Пауза здесь длится час, поэтому тест, который её досидит,
     * оборвётся по таймауту.
     */
    @Test
    void interruptedPause_returnsMessageWithoutWaitingOutTheBackoff() throws IOException {
        when(ingestService.receive(any(), any(), any(), any()))
                .thenThrow(new CannotAcquireLockException("база недоступна"));
        OneCQueueListener listener = listener(Duration.ofHours(1));
        Message message = delivery(envelope(MESSAGE_ID));

        boolean interruptKept = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Thread.currentThread().interrupt();
            listener.receive(OneCStream.MKB10, message, channel);
            return Thread.interrupted();
        });

        assertThat(interruptKept).isTrue();
        verify(channel).basicNack(DELIVERY_TAG, false, true);
    }

    /**
     * Сырьё совпадает с текстом, который прислала 1С, вместе с кириллицей, потому что тело читается как
     * UTF-8. Другая кодировка превратила бы наименования в мусор без единой ошибки.
     */
    @Test
    void cyrillicBody_isStoredAsSent() throws IOException {
        String body = """
                {"data":{"code":"A00","name":"Холера"},\
                "metadata":{"message-id":"%s","producer":"1c-usk"}}"""
                .formatted(MESSAGE_ID);

        listener().receive(OneCStream.MKB10, delivery(body), channel);

        verify(ingestService).receive(MESSAGE_ID, OneCStream.MKB10, QUEUE, body);
    }

    /**
     * Сорвавшийся коммит, который Spring не переводит в исключение доступа к данным, приходит как
     * {@link TransactionSystemException}, а не как {@code DataAccessException}. Сообщение и тогда
     * возвращается в очередь, потому что ловить одни исключения доступа к данным было бы мало.
     */
    @Test
    void commitFailure_returnsMessageToQueue() throws IOException {
        when(ingestService.receive(any(), any(), any(), any()))
                .thenThrow(new TransactionSystemException("коммит не удался"));

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
    }

    /**
     * {@link IOException} канала на подтверждении не является сбоем хранения, потому что сырьё уже
     * лежит в базе. Ошибка уходит контейнеру, а возврата в очередь нет. Неподтверждённое сообщение
     * брокер передоставит сам, и дедупликация примет его как дубль.
     *
     * <p>Это касается только {@code IOException}. Закрытый канал за прокси
     * {@code CachingConnectionFactory} бросает на подтверждении {@code IllegalStateException}, и её
     * слушатель пока принимает за сбой хранения: держит паузу и пытается вернуть сообщение. Так решил
     * владелец, и это поведение пересмотрят вместе с доработкой приёма из очередей.
     */
    @Test
    void acknowledgementIoFailure_isNotTakenForStorageFailure() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenReturn(true);
        doThrow(new IOException("канал закрыт")).when(channel).basicAck(DELIVERY_TAG, false);

        assertThatIOException().isThrownBy(
                () -> listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel));

        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    private OneCQueueListener listener(Duration storageUnavailableBackoff) {
        return new OneCQueueListener(
                new OneCEnvelopeParser(new JacksonConfig().objectMapper()),
                ingestService,
                new IngestProperties(storageUnavailableBackoff));
    }

    private static String envelope(UUID messageId) {
        return envelope(messageId.toString());
    }

    private static String envelope(String messageId) {
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

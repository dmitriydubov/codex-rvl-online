package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;

/**
 * Проход срока хранения без базы: что он стирает и о чём сообщает. Что строки со стёртым
 * телом остаются в таблице и что справочные потоки не трогаются, проверяет на настоящей базе
 * {@link PersonalDataRetentionTest}; здесь — решения самого прохода.
 */
@ExtendWith(MockitoExtension.class)
class PayloadExpiryTest {

    private static final Duration PAYLOAD_TTL = Duration.ofDays(30);
    private static final int BATCH_SIZE = 7;
    private static final Instant RECEIVED_AT = Instant.parse("2026-01-10T09:15:00Z");
    private static final String QUEUE = "1c-usk_to_rvl-online_policies";

    @Mock
    private AgedInboundMessages agedMessages;

    @Mock
    private InboundMessageRepository repository;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        retentionLog().addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        retentionLog().detachAppender(logged);
        logged.stop();
    }

    /** Только поток полисов, не больше пачки, и тело каждой строки стирается и сохраняется. */
    @Test
    void expiredPolicyPayloads_areErasedAndSaved() {
        InboundMessage first = policy();
        InboundMessage second = policy();
        when(agedMessages.withPayload(OneCStream.POLICIES, PAYLOAD_TTL, BATCH_SIZE)).thenReturn(List.of(first, second));

        retention().forgetExpiredPayloads();

        assertThat(first.payload()).isNull();
        assertThat(second.payload()).isNull();
        verify(repository).save(first);
        verify(repository).save(second);
    }

    /** В журнал — число и срок, без единого значения из стёртых тел. */
    @Test
    void erasure_isReportedWithCountAndTtl() {
        when(agedMessages.withPayload(OneCStream.POLICIES, PAYLOAD_TTL, BATCH_SIZE)).thenReturn(List.of(policy()));

        retention().forgetExpiredPayloads();

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("стёрты тела 1 сообщений полисов старше PT720H: срок хранения персональных данных");
        });
    }

    @Test
    void nothingExpired_nothingIsSavedOrLogged() {
        when(agedMessages.withPayload(OneCStream.POLICIES, PAYLOAD_TTL, BATCH_SIZE)).thenReturn(List.of());

        retention().forgetExpiredPayloads();

        verifyNoInteractions(repository);
        assertThat(logged.list).isEmpty();
    }

    private PersonalDataRetention retention() {
        return new PersonalDataRetention(agedMessages, repository, new ImportProperties(Duration.ofSeconds(10),
                Duration.ofSeconds(30), 100, Duration.ofHours(1), PAYLOAD_TTL, BATCH_SIZE));
    }

    private static InboundMessage policy() {
        return InboundMessage.receive(UUID.randomUUID(), OneCStream.POLICIES, QUEUE, "{\"id\":\"полис\"}", RECEIVED_AT);
    }

    private static Logger retentionLog() {
        return (Logger) LoggerFactory.getLogger(PersonalDataRetention.class);
    }
}

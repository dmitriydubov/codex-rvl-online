package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import ru.projectx.datareference.support.ImportSettings;

/**
 * Проход срока хранения без базы: сколько пачек он стирает — в сырье и в карантине, — каждую ли в
 * своей транзакции и о чём сообщает. Что запросы стирают только тела старше срока и оставляют сами строки,
 * проверяет на настоящей базе {@link PersonalDataRetentionTest}; здесь — решения самого прохода.
 *
 * <p>Транзакции подменены записью: каждая пачка обязана стираться внутри своей транзакции, а проход в целом —
 * не держать ни одной. Иначе транзакция на весь остаток держала бы блокировки строк, которые в это же время
 * читает разбор.
 */
@ExtendWith(MockitoExtension.class)
class PayloadExpiryTest {

    private static final Duration PAYLOAD_TTL = Duration.ofDays(30);
    private static final Duration QUARANTINE_TTL = Duration.ofDays(7);
    private static final int BATCH_SIZE = 7;

    @Mock
    private AgedInboundMessages agedMessages;

    private final RecordingTransactions transactions = new RecordingTransactions();

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

    /** Полные пачки — повод для следующей; неполная означает, что остаток исчерпан, и проход заканчивается. */
    @Test
    void remainderLargerThanBatch_isErasedInSeveralBatches() {
        when(agedMessages.forgetPayloads(PAYLOAD_TTL, BATCH_SIZE))
                .thenReturn(BATCH_SIZE, BATCH_SIZE, 3);
        when(agedMessages.forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE)).thenReturn(0);

        retention().forgetExpiredPayloads();

        verify(agedMessages, times(3)).forgetPayloads(PAYLOAD_TTL, BATCH_SIZE);
        verify(agedMessages).forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE);
        verifyNoMoreInteractions(agedMessages);
    }

    /** Карантин стирается тем же правилом пачек и со своим сроком, независимо от остатка в сырье. */
    @Test
    void quarantineRemainder_isErasedInItsOwnBatchesWithItsOwnTtl() {
        when(agedMessages.forgetPayloads(PAYLOAD_TTL, BATCH_SIZE)).thenReturn(0);
        when(agedMessages.forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE)).thenReturn(BATCH_SIZE, 2);

        retention().forgetExpiredPayloads();

        verify(agedMessages, times(2)).forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE);
        assertThat(logged.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                .isEqualTo("стёрты тела 9 карантинных сообщений старше PT168H: срок хранения персональных данных"));
    }

    /** Каждая пачка — в своей транзакции, и ни один запрос не идёт вне транзакции. */
    @Test
    void everyBatch_runsInItsOwnTransaction() {
        when(agedMessages.forgetPayloads(PAYLOAD_TTL, BATCH_SIZE)).thenAnswer(invocation -> {
            transactions.erasedInsideCurrent();
            return transactions.opened() < 2 ? BATCH_SIZE : 0;
        });
        when(agedMessages.forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE)).thenAnswer(invocation -> {
            transactions.erasedInsideCurrent();
            return 0;
        });

        retention().forgetExpiredPayloads();

        assertThat(transactions.erasuresPerTransaction()).containsExactly(1, 1, 1);
    }

    /** Остаток ровно в пачку: ещё один короткий запрос, который ничего не находит, — и проход окончен. */
    @Test
    void remainderOfExactlyOneBatch_endsOnEmptyBatch() {
        when(agedMessages.forgetPayloads(PAYLOAD_TTL, BATCH_SIZE)).thenReturn(BATCH_SIZE, 0);
        when(agedMessages.forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE)).thenReturn(0);

        retention().forgetExpiredPayloads();

        verify(agedMessages, times(2)).forgetPayloads(PAYLOAD_TTL, BATCH_SIZE);
    }

    /** В журнал — сколько тел стёрто за весь проход и какой срок, без единого значения из стёртых тел. */
    @Test
    void erasure_isReportedOnceWithTotalAndTtl() {
        when(agedMessages.forgetPayloads(PAYLOAD_TTL, BATCH_SIZE)).thenReturn(BATCH_SIZE, 3);
        when(agedMessages.forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE)).thenReturn(0);

        retention().forgetExpiredPayloads();

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("стёрты тела 10 сообщений в сырье старше PT720H: срок хранения персональных данных");
        });
    }

    /** Стирать нечего — по одному запросу на сырьё и карантин, по одной транзакции и молчание в журнале. */
    @Test
    void nothingExpired_oneQueryEachAndNothingLogged() {
        when(agedMessages.forgetPayloads(PAYLOAD_TTL, BATCH_SIZE)).thenReturn(0);
        when(agedMessages.forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE)).thenReturn(0);

        retention().forgetExpiredPayloads();

        verify(agedMessages).forgetPayloads(PAYLOAD_TTL, BATCH_SIZE);
        verify(agedMessages).forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE);
        assertThat(transactions.opened()).isEqualTo(2);
        assertThat(logged.list).isEmpty();
    }

    private PersonalDataRetention retention() {
        return new PersonalDataRetention(agedMessages, transactions, ImportSettings.defaults()
                .payloadTtl(PAYLOAD_TTL).quarantineTtl(QUARANTINE_TTL).retentionBatchSize(BATCH_SIZE).build());
    }

    private static Logger retentionLog() {
        return (Logger) LoggerFactory.getLogger(PersonalDataRetention.class);
    }

    /**
     * Транзакции, которые только запоминают, сколько стираний случилось внутри каждой.
     *
     * <p>Ни одному тесту здесь не нужно больше трёх пачек. Если проход перестанет останавливаться на неполной
     * пачке, он будет открывать транзакции бесконечно, и тест не упал бы, а завис. Поэтому после
     * {@link #MAX_TRANSACTIONS} транзакций подмена сама роняет тест с понятной причиной.
     */
    private static final class RecordingTransactions implements TransactionOperations {

        private static final int MAX_TRANSACTIONS = 10;

        private final List<Integer> erasures = new ArrayList<>();
        private boolean active;

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            assertThat(erasures.size())
                    .as("проход не остановился: открыто уже %d транзакций", erasures.size())
                    .isLessThan(MAX_TRANSACTIONS);
            erasures.add(0);
            active = true;
            try {
                TransactionStatus status = new SimpleTransactionStatus();
                return action.doInTransaction(status);
            }
            finally {
                active = false;
            }
        }

        void erasedInsideCurrent() {
            assertThat(active).as("стирание вне транзакции").isTrue();
            int current = erasures.size() - 1;
            erasures.set(current, erasures.get(current) + 1);
        }

        int opened() {
            return erasures.size();
        }

        List<Integer> erasuresPerTransaction() {
            return erasures;
        }
    }
}

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
import ru.projectx.datareference.service.retention.ExpiredPolicyPersonalData;
import ru.projectx.datareference.support.ImportSettings;

@ExtendWith(MockitoExtension.class)
class PayloadExpiryTest {

    private static final Duration PAYLOAD_TTL = Duration.ofDays(30);
    private static final Duration QUARANTINE_TTL = Duration.ofDays(7);
    private static final int BATCH_SIZE = 7;

    @Mock
    private AgedInboundMessages agedMessages;

    @Mock
    private ExpiredPolicyPersonalData expiredPolicies;

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

    @Test
    void remainderLargerThanBatch_isErasedInSeveralBatches() {
        when(agedMessages.forgetPayloads(PAYLOAD_TTL, BATCH_SIZE))
                .thenReturn(BATCH_SIZE, BATCH_SIZE, 3);
        when(agedMessages.forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE)).thenReturn(0);

        retention().forgetExpiredPersonalData();

        verify(agedMessages, times(3)).forgetPayloads(PAYLOAD_TTL, BATCH_SIZE);
        verify(agedMessages).forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE);
        verifyNoMoreInteractions(agedMessages);
    }

    @Test
    void quarantineRemainder_isErasedInItsOwnBatchesWithItsOwnTtl() {
        when(agedMessages.forgetPayloads(PAYLOAD_TTL, BATCH_SIZE)).thenReturn(0);
        when(agedMessages.forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE)).thenReturn(BATCH_SIZE, 2);

        retention().forgetExpiredPersonalData();

        verify(agedMessages, times(2)).forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE);
        assertThat(logged.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                .isEqualTo("стёрты тела 9 карантинных сообщений старше PT168H: срок хранения персональных данных"));
    }

    @Test
    void policyRemainder_isErasedInBatches_andReportedOnceWithGrace() {
        when(agedMessages.forgetPayloads(PAYLOAD_TTL, BATCH_SIZE)).thenReturn(0);
        when(agedMessages.forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE)).thenReturn(0);
        when(expiredPolicies.erase(BATCH_SIZE)).thenReturn(BATCH_SIZE, 1);

        retention().forgetExpiredPersonalData();

        verify(expiredPolicies, times(2)).erase(BATCH_SIZE);
        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).isEqualTo(
                    "обезличены полисы: 8, после окончания срока или перевода в архив прошло больше PT720H");
        });
    }

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
        when(expiredPolicies.erase(BATCH_SIZE)).thenAnswer(invocation -> {
            transactions.erasedInsideCurrent();
            return 0;
        });

        retention().forgetExpiredPersonalData();

        assertThat(transactions.erasuresPerTransaction()).containsExactly(1, 1, 1, 1);
    }

    @Test
    void remainderOfExactlyOneBatch_endsOnEmptyBatch() {
        when(agedMessages.forgetPayloads(PAYLOAD_TTL, BATCH_SIZE)).thenReturn(BATCH_SIZE, 0);
        when(agedMessages.forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE)).thenReturn(0);

        retention().forgetExpiredPersonalData();

        verify(agedMessages, times(2)).forgetPayloads(PAYLOAD_TTL, BATCH_SIZE);
    }

    @Test
    void erasure_isReportedOnceWithTotalAndTtl() {
        when(agedMessages.forgetPayloads(PAYLOAD_TTL, BATCH_SIZE)).thenReturn(BATCH_SIZE, 3);
        when(agedMessages.forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE)).thenReturn(0);

        retention().forgetExpiredPersonalData();

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("стёрты тела 10 сообщений в сырье старше PT720H: срок хранения персональных данных");
        });
    }

    @Test
    void nothingExpired_oneQueryEachAndNothingLogged() {
        when(agedMessages.forgetPayloads(PAYLOAD_TTL, BATCH_SIZE)).thenReturn(0);
        when(agedMessages.forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE)).thenReturn(0);

        retention().forgetExpiredPersonalData();

        verify(agedMessages).forgetPayloads(PAYLOAD_TTL, BATCH_SIZE);
        verify(agedMessages).forgetQuarantineBodies(QUARANTINE_TTL, BATCH_SIZE);
        verify(expiredPolicies).erase(BATCH_SIZE);
        assertThat(transactions.opened()).isEqualTo(3);
        assertThat(logged.list).isEmpty();
    }

    private PersonalDataRetention retention() {
        return new PersonalDataRetention(agedMessages, expiredPolicies, transactions, ImportSettings.defaults()
                .payloadTtl(PAYLOAD_TTL).quarantineTtl(QUARANTINE_TTL).retentionBatchSize(BATCH_SIZE).build());
    }

    private static Logger retentionLog() {
        return (Logger) LoggerFactory.getLogger(PersonalDataRetention.class);
    }

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

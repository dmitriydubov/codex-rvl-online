package ru.projectx.datareference.service.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.BeanFactoryCacheOperationSourceAdvisor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.datareference.config.ReferenceCaches;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.inbound.OneCMessageApplied;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class ReferenceCacheTransactionTest {

    private static final String KEY = "J00";

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private BeanFactoryCacheOperationSourceAdvisor cacheAdvisor;

    @Autowired
    private BeanFactoryTransactionAttributeSourceAdvisor transactionAdvisor;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void cacheHoldsAnswer() {
        databaseCleaner.clear();
        mkb10Codes().put(KEY, "ответ");
    }

    @Test
    void cacheIsClearedOnlyAfterCommit() {
        transactions.executeWithoutResult(status -> {
            events.publishEvent(new OneCMessageApplied(OneCStream.MKB10));
            assertThat(mkb10Codes().get(KEY)).as("до фиксации").isNotNull();
        });

        assertThat(mkb10Codes().get(KEY)).as("после фиксации").isNull();
    }

    @Test
    void rolledBackTransaction_leavesCache() {
        transactions.executeWithoutResult(status -> {
            events.publishEvent(new OneCMessageApplied(OneCStream.MKB10));
            status.setRollbackOnly();
        });

        assertThat(mkb10Codes().get(KEY)).isNotNull();
    }

    @Test
    void cacheRunsBeforeReadTransaction() {
        assertThat(cacheAdvisor.getOrder()).isLessThan(transactionAdvisor.getOrder());
    }

    private Cache mkb10Codes() {
        return Objects.requireNonNull(cacheManager.getCache(ReferenceCaches.MKB10_CODES));
    }
}

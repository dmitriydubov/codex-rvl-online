package ru.projectx.datareference.service.schedulers;

import java.time.Duration;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.service.retention.ExpiredPolicyPersonalData;

@Component
public class PersonalDataRetention {

    private static final Logger log = LoggerFactory.getLogger(PersonalDataRetention.class);

    private final AgedInboundMessages agedMessages;
    private final ExpiredPolicyPersonalData expiredPolicies;
    private final TransactionOperations transactions;
    private final ImportProperties properties;

    public PersonalDataRetention(AgedInboundMessages agedMessages, ExpiredPolicyPersonalData expiredPolicies,
            TransactionOperations transactions, ImportProperties properties) {

        this.agedMessages = agedMessages;
        this.expiredPolicies = expiredPolicies;
        this.transactions = transactions;
        this.properties = properties;
    }

    public void forgetExpiredPersonalData() {
        int inbound = eraseInBatches(
                () -> agedMessages.forgetPayloads(properties.payloadTtl(), properties.retentionBatchSize()));
        report(inbound, "сообщений в сырье", properties.payloadTtl());

        int quarantined = eraseInBatches(
                () -> agedMessages.forgetQuarantineBodies(properties.quarantineTtl(), properties.retentionBatchSize()));
        report(quarantined, "карантинных сообщений", properties.quarantineTtl());

        int policies = eraseInBatches(() -> expiredPolicies.erase(properties.retentionBatchSize()));
        reportPolicies(policies);
    }

    private int eraseInBatches(IntSupplier batch) {
        int total = 0;
        int erased;
        do {
            erased = transactions.execute(status -> batch.getAsInt());
            total += erased;
        }
        while (erased == properties.retentionBatchSize());
        return total;
    }

    private static void report(int erased, String what, Duration ttl) {
        if (erased > 0) {
            log.info("стёрты тела {} {} старше {}: срок хранения персональных данных", erased, what, ttl);
        }
    }

    private void reportPolicies(int erased) {
        if (erased > 0) {
            log.info("обезличены полисы: {}, после окончания срока или перевода в архив прошло больше {}", erased,
                    properties.policyPersonalDataGrace());
        }
    }
}

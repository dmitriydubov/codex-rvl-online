package ru.projectx.datareference.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;
import ru.projectx.datareference.entity.PriceListFinalization;
import ru.projectx.datareference.service.SqlStates;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.TestJdbc;

/**
 * Хранение последней применённой передачи прайса на настоящем PostgreSQL: блокировка строки услуги, чтение услуги
 * зависшей передачи из тела и заполнение таблицы из сырья при обновлении. Всё это проверяет только база.
 */
@IntegrationTest
class PriceListFinalizationStorageTest {

    private static final UUID SERVICE = UUID.fromString("2a6f0c3e-9b18-4d57-8e41-c5d2a7f9b063");
    private static final UUID OTHER_SERVICE = UUID.fromString("41c8e2f7-9a3d-4b65-8e10-c2d7f4a9b356");
    private static final UUID CID = UUID.fromString("7d3e9a10-4b2c-4f8e-9a61-0c5b8e2f4d17");
    private static final Instant EARLIER = Instant.parse("2026-03-14T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-03-14T10:00:00Z");
    private static final String FILL_CHANGE_SET = "017-price-list-finalization-fill";
    /** SQLSTATE lock_not_available: так PostgreSQL отменяет оператор по lock_timeout. */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    @Autowired
    private PriceListFinalizationRepository finalizations;

    @Autowired
    private InboundMessageRepository inbound;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private TestJdbc testJdbc;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    /** Пока одна транзакция держит строку услуги, вторая её не получает: ждёт и уходит с временным отказом. */
    @Test
    void lockedServiceRow_isNotGivenToAnotherTransaction() throws SQLException {
        UUID finalId = part("PROCESSED", SERVICE, 1, EARLIER);
        testJdbc.update("insert into price_list_finalization (service_external_id, final_message_id) values (?, ?)",
                SERVICE, finalId);

        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement(
                    "select 1 from price_list_finalization where service_external_id = ? for update")) {
                lock.setObject(1, SERVICE);
                lock.executeQuery();
            }

            assertThatThrownBy(() -> inTransaction(() -> finalizations.lockByService(SERVICE)))
                    .isInstanceOf(PessimisticLockingFailureException.class)
                    .satisfies(failure -> assertThat(SqlStates.deepest(failure)).contains(LOCK_NOT_AVAILABLE));
            holder.rollback();
        }
    }

    @Test
    void freeServiceRow_isLockedAndRead() {
        UUID finalId = part("PROCESSED", SERVICE, 1, EARLIER);
        finalizations.save(PriceListFinalization.of(SERVICE, finalId));

        Optional<PriceListFinalization> locked = inTransaction(() -> finalizations.lockByService(SERVICE));

        assertThat(locked).map(PriceListFinalization::finalMessageId).contains(finalId);
        assertThat(inTransaction(() -> finalizations.lockByService(OTHER_SERVICE))).isEmpty();
    }

    /** Услуга берётся из финального сообщения, если оно в буфере, иначе из самой ранней части. */
    @Test
    void serviceOfBufferedTransfer_isReadFromFinalFirst() {
        part("BUFFERED", OTHER_SERVICE, null, EARLIER);
        part("BUFFERED", SERVICE, 1, LATER);

        assertThat(inbound.findBufferedTransferService(CID)).contains(SERVICE.toString());
    }

    @Test
    void serviceOfBufferedTransferWithoutFinal_isReadFromEarliestPart() {
        part("BUFFERED", SERVICE, null, EARLIER);
        part("BUFFERED", OTHER_SERVICE, null, LATER);

        assertThat(inbound.findBufferedTransferService(CID)).contains(SERVICE.toString());
    }

    /** Части с записанным исходом не в буфере: закрывать такую передачу нечего. */
    @Test
    void transferWithoutBufferedParts_hasNoServiceToClose() {
        part("SKIPPED", SERVICE, 1, EARLIER);

        assertThat(inbound.findBufferedTransferService(CID)).isEmpty();
    }

    /**
     * Обновление заполняет по каждой услуге последнюю применённую передачу: финальное сообщение в статусе
     * «применено», принятое позже всех. Пакеты, пропущенные финалы и GUID не в канонической записи не учитываются.
     */
    @Test
    void fill_takesLatestAppliedFinalOfEveryService() {
        part("PROCESSED", SERVICE, 1, EARLIER);
        UUID latest = part("PROCESSED", SERVICE, 1, LATER);
        part("SKIPPED", SERVICE, 1, LATER.plusSeconds(60));
        part("PROCESSED", SERVICE, null, LATER.plusSeconds(120));
        UUID otherServiceFinal = part("PROCESSED", OTHER_SERVICE, 0, EARLIER);
        partWithKey("PROCESSED", "{2a6f0c3e-9b18-4d57-8e41-c5d2a7f9b064}", LATER);

        runFill();

        assertThat(finalizationsByService()).containsExactlyInAnyOrderEntriesOf(Map.of(
                SERVICE, latest, OTHER_SERVICE, otherServiceFinal));
    }

    /** Повторный запуск ничего не меняет, а уже записанная последняя передача не перезаписывается. */
    @Test
    void fill_isIdempotent_andKeepsExistingRows() {
        UUID recorded = part("PROCESSED", SERVICE, 1, EARLIER);
        part("PROCESSED", SERVICE, 1, LATER);
        UUID otherServiceFinal = part("PROCESSED", OTHER_SERVICE, 1, EARLIER);
        finalizations.save(PriceListFinalization.of(SERVICE, recorded));

        runFill();
        runFill();

        assertThat(finalizationsByService()).containsExactlyInAnyOrderEntriesOf(Map.of(
                SERVICE, recorded, OTHER_SERVICE, otherServiceFinal));
    }

    @Test
    void fill_onEmptyRawTable_addsNothing() {
        runFill();

        assertThat(finalizations.count()).isZero();
    }

    private Map<UUID, UUID> finalizationsByService() {
        return finalizations.findAll().stream().collect(Collectors.toMap(
                PriceListFinalization::serviceExternalId, PriceListFinalization::finalMessageId));
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactions).execute(status -> work.get());
    }

    private UUID part(String status, UUID service, Integer batchCount, Instant receivedAt) {
        return partWithKey(status, service.toString(), receivedAt, batchCount);
    }

    private UUID partWithKey(String status, String serviceKey, Instant receivedAt) {
        return partWithKey(status, serviceKey, receivedAt, 1);
    }

    private UUID partWithKey(String status, String serviceKey, Instant receivedAt, Integer batchCount) {
        UUID messageId = UUID.randomUUID();
        testJdbc.update("""
                insert into inbound_message (message_id, stream, source_queue, payload, status, attempt, received_at,
                                             correlation_id, batch_count, row_count)
                values (?, 'PRICE_LISTS', 'очередь', cast(? as jsonb), ?, 0, ?, ?, ?, 0)
                """, messageId, "{\"data\":{\"cid\":\"%s\",\"keyValue\":\"%s\"}}".formatted(CID, serviceKey),
                status, Timestamp.from(receivedAt), CID, batchCount);
        return messageId;
    }

    /** Выполняет заполнение ровно тем текстом, что лежит в миграции. */
    private void runFill() {
        testJdbc.update(fillSql());
    }

    private static String fillSql() {
        try (InputStream changelog = new ClassPathResource("db/changelog/017-price-list-finalization.yaml")
                .getInputStream()) {
            Map<String, Object> root = new Yaml().load(changelog);
            return ((List<Map<String, Object>>) root.get("databaseChangeLog")).stream()
                    .map(entry -> (Map<String, Object>) entry.get("changeSet"))
                    .filter(changeSet -> FILL_CHANGE_SET.equals(changeSet.get("id")))
                    .map(changeSet -> ((List<Map<String, Map<String, String>>>) changeSet.get("changes"))
                            .getFirst().get("sql").get("sql"))
                    .findFirst()
                    .orElseThrow();
        }
        catch (IOException unreadable) {
            throw new IllegalStateException("миграция заполнения не читается", unreadable);
        }
    }
}

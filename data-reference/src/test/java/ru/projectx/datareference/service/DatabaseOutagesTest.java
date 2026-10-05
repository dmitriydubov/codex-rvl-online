package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.stream.Stream;
import org.hibernate.LazyInitializationException;
import org.hibernate.TransactionException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.GenericJDBCException;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.orm.jpa.hibernate.HibernateExceptionTranslator;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

/**
 * Временный отказ базы распознаётся и тогда, когда исключение пришло сырым из Hibernate — с ленивой
 * догрузки в сервисе, мимо репозитория. Без этого отмена оператора по таймауту на догрузке программ
 * полиса уходила бы как внутренняя ошибка, которую сервис Заявок не повторяет. Переводчик — тот же
 * класс Spring, которым переводят исключения прокси репозиториев, поэтому тест проверяет настоящий перевод.
 */
class DatabaseOutagesTest {

    private static final String TEXT = "canceling statement due to user request";
    private static final String QUERY_CANCELED = "57014";
    private static final String CONNECTION_FAILURE = "08006";

    private final DatabaseOutages outages = new DatabaseOutages(new HibernateExceptionTranslator());

    /** Отмена оператора по таймауту на ленивой догрузке: Hibernate сообщает своим типом, с SQLSTATE 57014. */
    @Test
    void hibernateQueryTimeout_isTemporary_namedByTranslatedTypeWithSqlState() {
        var timeout = new org.hibernate.QueryTimeoutException(TEXT, new SQLException(TEXT, QUERY_CANCELED), "select 1");

        Optional<DatabaseOutages.Outage> outage = outages.recognize(timeout);

        assertThat(outage).hasValue(new DatabaseOutages.Outage("QueryTimeoutException", QUERY_CANCELED));
    }

    /** Истёкший лимит транзакции Hibernate проверяет сам, перед оператором, и бросает без причины из JDBC. */
    @Test
    void rawHibernateTransactionTimeout_isTemporary() {
        Optional<DatabaseOutages.Outage> outage = outages.recognize(new TransactionException("Transaction timeout expired"));

        assertThat(outage).hasValue(new DatabaseOutages.Outage("JpaSystemException/TransactionException", "нет"));
    }

    /** Та же обёртка, уже сделанная Spring на пути через репозиторий: в журнале видна причина, а не только обёртка. */
    @Test
    void wrappedHibernateTransactionTimeout_isTemporary_namedWithItsCause() {
        var wrapped = new JpaSystemException(new TransactionException("Transaction timeout expired"));

        Optional<DatabaseOutages.Outage> outage = outages.recognize(wrapped);

        assertThat(outage).hasValue(new DatabaseOutages.Outage("JpaSystemException/TransactionException", "нет"));
    }

    /** При обрыве соединения драйвер кладёт SQLSTATE класса 08 поверх IOException: нужен не корень, а ближайший SQLException. */
    @Test
    void connectionLoss_carriesSqlStateOfDeepestSqlException_notOfRootCause() {
        var lost = new JDBCConnectionException(TEXT, new SQLException(TEXT, CONNECTION_FAILURE, new IOException("reset")));

        Optional<DatabaseOutages.Outage> outage = outages.recognize(lost);

        assertThat(outage).hasValue(new DatabaseOutages.Outage("DataAccessResourceFailureException", CONNECTION_FAILURE));
    }

    static Stream<RuntimeException> springOutages() {
        return Stream.of(
                new CannotCreateTransactionException(TEXT, new SQLException(TEXT, CONNECTION_FAILURE)),
                new DataAccessResourceFailureException(TEXT),
                new QueryTimeoutException(TEXT),
                new CannotAcquireLockException(TEXT),
                new TransactionTimedOutException(TEXT));
    }

    /** Уже переведённые Spring отказы остаются временными и после прохода через переводчик. */
    @ParameterizedTest
    @MethodSource("springOutages")
    void springOutage_staysTemporary_namedByItsOwnType(RuntimeException failure) {
        Optional<DatabaseOutages.Outage> outage = outages.recognize(failure);

        assertThat(outage).map(DatabaseOutages.Outage::type).hasValue(failure.getClass().getSimpleName());
    }

    static Stream<Exception> permanentFailures() {
        return Stream.of(
                new ConstraintViolationException(TEXT, new SQLException(TEXT, "23505"), "uq_policies_external_id"),
                new LazyInitializationException("no session"),
                new DataIntegrityViolationException(TEXT),
                new JpaSystemException(new IllegalStateException(TEXT)),
                new IllegalStateException(TEXT),
                new Exception(TEXT));
    }

    /** Нарушение ограничения, ошибка программиста или чужое исключение повтором не лечатся. */
    @ParameterizedTest
    @MethodSource("permanentFailures")
    void permanentFailure_isNotAnOutage(Exception failure) {
        assertThat(outages.recognize(failure)).isEmpty();
    }

    /**
     * Постоянный отказ базы тоже описывается для журнала: тип после перевода и SQLSTATE. По ним разбор сообщений
     * 1С:УСК называет причину окончательного отказа, не повторяя текста ошибки базы.
     */
    @Test
    void permanentDatabaseFailure_isDescribedByTranslatedTypeWithSqlState() {
        var overflow = new ConstraintViolationException(TEXT, new SQLException(TEXT, "23514"), "ck_percent");

        Optional<DatabaseOutages.Outage> described = outages.describe(overflow);

        assertThat(described).hasValue(new DatabaseOutages.Outage("DataIntegrityViolationException", "23514"));
    }

    /** Временный отказ описывается так же, как его распознаёт {@code recognize}. */
    @Test
    void temporaryFailure_isDescribedLikeItIsRecognized() {
        var lost = new JDBCConnectionException(TEXT, new SQLException(TEXT, CONNECTION_FAILURE));

        assertThat(outages.describe(lost)).isEqualTo(outages.recognize(lost)).isPresent();
    }

    /**
     * Ошибка не из базы не описывается, даже если переводчик называет её ошибкой доступа к данным: обычные
     * {@code IllegalStateException} и {@code IllegalArgumentException} он считает неверным вызовом JPA.
     */
    @Test
    void failureOutsideTheDatabase_isNotDescribed() {
        assertThat(outages.describe(new IllegalStateException(TEXT))).isEmpty();
        assertThat(outages.describe(new IllegalArgumentException(TEXT))).isEmpty();
        assertThat(outages.describe(new Exception(TEXT))).isEmpty();
    }

    /**
     * Отказ на входе в транзакцию — исключение транзакций Spring, а не доступа к данным, но это временный отказ
     * базы, и он описывается так же, как распознаётся: своим типом и SQLSTATE драйвера.
     */
    @Test
    void transactionLevelOutage_isDescribedByItsOwnTypeWithSqlState() {
        var noConnection = new CannotCreateTransactionException(TEXT, new SQLException(TEXT, CONNECTION_FAILURE));

        assertThat(outages.describe(noConnection))
                .hasValue(new DatabaseOutages.Outage("CannotCreateTransactionException", CONNECTION_FAILURE));
    }

    /** Исключение JPA без перевода в Hibernate — тоже отказ базы, и описывается переведённым типом. */
    @Test
    void persistenceException_isDescribed() {
        assertThat(outages.describe(new jakarta.persistence.PersistenceException(TEXT)))
                .hasValue(new DatabaseOutages.Outage("JpaSystemException", "нет"));
    }

    /** Уже переведённое исключение Spring описывается своим типом. */
    @Test
    void springDataAccessFailure_isDescribedByItsOwnType() {
        assertThat(outages.describe(new DataIntegrityViolationException(TEXT)))
                .hasValue(new DatabaseOutages.Outage("DataIntegrityViolationException", "нет"));
    }

    /**
     * Сеанс, закрытый сервером по простою открытой транзакции (25P03), — временный отказ: пул заменит
     * соединение, и та же операция пройдёт. Перевод даёт общий {@code JpaSystemException}, поэтому отказ
     * узнаётся по коду. Так REST отвечает 503, а разбор сообщения 1С:УСК идёт на повтор.
     */
    @Test
    void sessionClosedForIdleTransaction_isTemporary_recognizedBySqlState() {
        var closed = new GenericJDBCException(TEXT, new SQLException(TEXT, "25P03"));

        assertThat(outages.recognize(closed)).hasValue(new DatabaseOutages.Outage("JpaSystemException", "25P03"));
    }

    /** Остальные коды класса 25 — ошибки кода в обращении с транзакцией: повтор их не лечит. */
    @ParameterizedTest
    @ValueSource(strings = {"25001", "25P02"})
    void otherTransactionStateCodes_areNotOutages(String sqlState) {
        var failure = new GenericJDBCException(TEXT, new SQLException(TEXT, sqlState));

        assertThat(outages.recognize(failure)).isEmpty();
    }
}

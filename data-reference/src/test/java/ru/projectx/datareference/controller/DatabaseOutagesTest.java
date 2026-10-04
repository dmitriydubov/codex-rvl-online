package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.stream.Stream;
import org.hibernate.LazyInitializationException;
import org.hibernate.TransactionException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.Test;
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

        assertThat(outage).hasValue(new DatabaseOutages.Outage("JpaSystemException", "нет"));
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
                new TransactionTimedOutException(TEXT),
                new JpaSystemException(new TransactionException("Transaction timeout expired")));
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
}

package ru.projectx.datareference.service.database;

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
import org.springframework.data.jpa.repository.query.BadJpqlGrammarException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.orm.jpa.hibernate.HibernateExceptionTranslator;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

class DatabaseOutagesTest {

    private static final String TEXT = "canceling statement due to user request";
    private static final String QUERY_CANCELED = "57014";
    private static final String CONNECTION_FAILURE = "08006";
    private static final String JPQL = "select p from Policy p where p.externalId = ?1";

    private final DatabaseOutages outages = new DatabaseOutages(new HibernateExceptionTranslator());

    @Test
    void hibernateQueryTimeout_isTemporary_namedByTranslatedTypeWithSqlState() {
        var timeout = new org.hibernate.QueryTimeoutException(TEXT, new SQLException(TEXT, QUERY_CANCELED), "select 1");

        Optional<DatabaseOutages.Outage> outage = outages.recognize(timeout);

        assertThat(outage).hasValue(new DatabaseOutages.Outage("QueryTimeoutException", QUERY_CANCELED));
    }

    @Test
    void rawHibernateTransactionTimeout_isTemporary() {
        Optional<DatabaseOutages.Outage> outage = outages.recognize(new TransactionException("Transaction timeout expired"));

        assertThat(outage).hasValue(new DatabaseOutages.Outage("JpaSystemException/TransactionException", "нет"));
    }

    @Test
    void wrappedHibernateTransactionTimeout_isTemporary_namedWithItsCause() {
        var wrapped = new JpaSystemException(new TransactionException("Transaction timeout expired"));

        Optional<DatabaseOutages.Outage> outage = outages.recognize(wrapped);

        assertThat(outage).hasValue(new DatabaseOutages.Outage("JpaSystemException/TransactionException", "нет"));
    }

    @Test
    void connectionLoss_carriesSqlStateOfDeepestSqlException_notOfRootCause() {
        var lost = new JDBCConnectionException(TEXT, new SQLException(TEXT, CONNECTION_FAILURE, new IOException("reset")));

        Optional<DatabaseOutages.Outage> outage = outages.recognize(lost);

        assertThat(outage).hasValue(new DatabaseOutages.Outage("DataAccessResourceFailureException", CONNECTION_FAILURE));
    }

    @Test
    void deadlineExpiredUnderDerivedQueryWrapper_isTemporary_namedByWrapperAndExpiredDeadline() {
        var hidden = new BadJpqlGrammarException(TEXT, JPQL, new TransactionTimedOutException(TEXT));

        Optional<DatabaseOutages.Outage> outage = outages.recognize(hidden);

        assertThat(outage).hasValue(new DatabaseOutages.Outage("BadJpqlGrammarException/TransactionTimedOutException", "нет"));
    }

    @Test
    void deadlineExpiredDeepInCauseChain_isTemporary() {
        var hidden = new BadJpqlGrammarException(TEXT, JPQL,
                new IllegalStateException(TEXT, new TransactionTimedOutException(TEXT)));

        assertThat(outages.recognize(hidden)).isPresent();
    }

    static Stream<RuntimeException> springOutages() {
        return Stream.of(
                new CannotCreateTransactionException(TEXT, new SQLException(TEXT, CONNECTION_FAILURE)),
                new DataAccessResourceFailureException(TEXT),
                new QueryTimeoutException(TEXT),
                new CannotAcquireLockException(TEXT),
                new TransactionTimedOutException(TEXT));
    }

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
                new BadJpqlGrammarException(TEXT, JPQL, new IllegalArgumentException(TEXT)),
                new BadJpqlGrammarException(TEXT, JPQL, new DataAccessResourceFailureException(TEXT)),
                new IllegalStateException(TEXT),
                new Exception(TEXT));
    }

    @ParameterizedTest
    @MethodSource("permanentFailures")
    void permanentFailure_isNotAnOutage(Exception failure) {
        assertThat(outages.recognize(failure)).isEmpty();
    }

    @Test
    void permanentDatabaseFailure_isDescribedByTranslatedTypeWithSqlState() {
        var overflow = new ConstraintViolationException(TEXT, new SQLException(TEXT, "23514"), "ck_percent");

        Optional<DatabaseOutages.Outage> described = outages.describe(overflow);

        assertThat(described).hasValue(new DatabaseOutages.Outage("DataIntegrityViolationException", "23514"));
    }

    @Test
    void temporaryFailure_isDescribedLikeItIsRecognized() {
        var lost = new JDBCConnectionException(TEXT, new SQLException(TEXT, CONNECTION_FAILURE));

        assertThat(outages.describe(lost)).isEqualTo(outages.recognize(lost)).isPresent();
    }

    @Test
    void failureOutsideTheDatabase_isNotDescribed() {
        assertThat(outages.describe(new IllegalStateException(TEXT))).isEmpty();
        assertThat(outages.describe(new IllegalArgumentException(TEXT))).isEmpty();
        assertThat(outages.describe(new Exception(TEXT))).isEmpty();
    }

    @Test
    void transactionLevelOutage_isDescribedByItsOwnTypeWithSqlState() {
        var noConnection = new CannotCreateTransactionException(TEXT, new SQLException(TEXT, CONNECTION_FAILURE));

        assertThat(outages.describe(noConnection))
                .hasValue(new DatabaseOutages.Outage("CannotCreateTransactionException", CONNECTION_FAILURE));
    }

    @Test
    void persistenceException_isDescribed() {
        assertThat(outages.describe(new jakarta.persistence.PersistenceException(TEXT)))
                .hasValue(new DatabaseOutages.Outage("JpaSystemException", "нет"));
    }

    @Test
    void springDataAccessFailure_isDescribedByItsOwnType() {
        assertThat(outages.describe(new DataIntegrityViolationException(TEXT)))
                .hasValue(new DatabaseOutages.Outage("DataIntegrityViolationException", "нет"));
    }

    @Test
    void sessionClosedForIdleTransaction_isTemporary_recognizedBySqlState() {
        var closed = new GenericJDBCException(TEXT, new SQLException(TEXT, "25P03"));

        assertThat(outages.recognize(closed)).hasValue(new DatabaseOutages.Outage("JpaSystemException", "25P03"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"25001", "25P02"})
    void otherTransactionStateCodes_areNotOutages(String sqlState) {
        var failure = new GenericJDBCException(TEXT, new SQLException(TEXT, sqlState));

        assertThat(outages.recognize(failure)).isEmpty();
    }
}

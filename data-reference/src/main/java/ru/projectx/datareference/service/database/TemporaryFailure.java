package ru.projectx.datareference.service.database;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

enum TemporaryFailure {

    TEMPORARY_TYPE {
        @Override
        boolean matches(Throwable failure) {
            return TEMPORARY_TYPES.stream().anyMatch(type -> type.isInstance(failure));
        }
    },

    EXPIRED_HIBERNATE_TRANSACTION {
        @Override
        boolean matches(Throwable failure) {
            return failure instanceof JpaSystemException && failure.getCause() instanceof org.hibernate.TransactionException;
        }

        @Override
        String typeOf(Throwable failure) {
            return wrapped(failure, failure.getCause().getClass());
        }
    },

    HIDDEN_EXPIRED_DEADLINE {
        @Override
        boolean matches(Throwable failure) {
            return Stream.iterate(failure.getCause(), Objects::nonNull, Throwable::getCause)
                    .anyMatch(TransactionTimedOutException.class::isInstance);
        }

        @Override
        String typeOf(Throwable failure) {
            return wrapped(failure, TransactionTimedOutException.class);
        }
    },

    IDLE_TRANSACTION_SESSION_CLOSED {
        @Override
        boolean matches(Throwable failure) {
            return SqlStates.deepest(failure).filter(IDLE_IN_TRANSACTION_SESSION_TIMEOUT::equals).isPresent();
        }
    };

    private static final List<Class<? extends RuntimeException>> TEMPORARY_TYPES = List.of(
            CannotCreateTransactionException.class,
            TransactionTimedOutException.class,
            DataAccessResourceFailureException.class,
            RecoverableDataAccessException.class,
            TransientDataAccessException.class);

    private static final String IDLE_IN_TRANSACTION_SESSION_TIMEOUT = "25P03";

    static Optional<TemporaryFailure> of(Throwable failure) {
        return Arrays.stream(values()).filter(kind -> kind.matches(failure)).findFirst();
    }

    abstract boolean matches(Throwable failure);

    String typeOf(Throwable failure) {
        return failure.getClass().getSimpleName();
    }

    private static String wrapped(Throwable failure, Class<?> cause) {
        return failure.getClass().getSimpleName() + "/" + cause.getSimpleName();
    }
}

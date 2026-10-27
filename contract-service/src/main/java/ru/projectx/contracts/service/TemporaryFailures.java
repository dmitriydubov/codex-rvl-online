package ru.projectx.contracts.service;

import java.sql.SQLTransientException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.dao.TransientDataAccessException;
import ru.projectx.contracts.exception.TemporaryFailureException;

public final class TemporaryFailures {

    private static final List<Class<? extends Throwable>> TEMPORARY_TYPES = List.of(TemporaryFailureException.class,
            TransientDataAccessException.class, SQLTransientException.class);

    private TemporaryFailures() {
    }

    public static boolean temporary(Throwable failure) {
        return temporaryCause(failure).isPresent() || transientSqlState(failure).isPresent();
    }

    public static Optional<String> serviceReason(Throwable failure) {
        return causes(failure).filter(TemporaryFailureException.class::isInstance)
                .findFirst()
                .map(Throwable::getMessage);
    }

    public static Optional<String> transientSqlState(Throwable failure) {
        return SqlStates.deepest(failure).filter(SqlStates::transientState);
    }

    public static Optional<Throwable> temporaryCause(Throwable failure) {
        return causes(failure).filter(TemporaryFailures::temporaryType).findFirst();
    }

    private static boolean temporaryType(Throwable cause) {
        return TEMPORARY_TYPES.stream().anyMatch(type -> type.isInstance(cause));
    }

    private static Stream<Throwable> causes(Throwable failure) {
        return Stream.iterate(failure, Objects::nonNull, Throwable::getCause);
    }
}

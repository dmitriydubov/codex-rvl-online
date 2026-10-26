package ru.projectx.contracts.service.usk;

import java.sql.SQLTransientException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.dao.TransientDataAccessException;
import ru.projectx.contracts.exception.TemporaryFailureException;
import ru.projectx.contracts.service.SqlStates;

public final class UskPreparationFailures {

    private static final List<Class<? extends Throwable>> TEMPORARY_TYPES = List.of(TemporaryFailureException.class,
            TransientDataAccessException.class, SQLTransientException.class);

    private UskPreparationFailures() {
    }

    public static boolean temporary(RuntimeException failure) {
        return causes(failure).anyMatch(UskPreparationFailures::temporaryType)
                || SqlStates.deepest(failure).filter(SqlStates::transientState).isPresent();
    }

    private static boolean temporaryType(Throwable cause) {
        return TEMPORARY_TYPES.stream().anyMatch(type -> type.isInstance(cause));
    }

    private static Stream<Throwable> causes(Throwable failure) {
        return Stream.iterate(failure, Objects::nonNull, Throwable::getCause);
    }
}

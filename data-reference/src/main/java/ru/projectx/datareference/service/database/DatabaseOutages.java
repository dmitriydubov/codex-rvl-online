package ru.projectx.datareference.service.database;

import jakarta.persistence.PersistenceException;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.stereotype.Component;

@Component
public class DatabaseOutages {

    private final PersistenceExceptionTranslator translator;

    public DatabaseOutages(PersistenceExceptionTranslator translator) {
        this.translator = translator;
    }

    public Optional<Outage> recognize(Exception failure) {
        return translated(failure).flatMap(DatabaseOutages::temporaryOutage);
    }

    public Optional<Outage> describe(Exception failure) {
        if (failure instanceof DataAccessException || failure instanceof PersistenceException) {
            return translated(failure).map(DatabaseOutages::outageOf);
        }
        return recognize(failure);
    }

    private Optional<Throwable> translated(Exception failure) {
        if (!(failure instanceof RuntimeException runtime)) {
            return Optional.empty();
        }
        return Optional.of(translate(runtime));
    }

    private Throwable translate(RuntimeException failure) {
        DataAccessException translated = translator.translateExceptionIfPossible(failure);
        return translated == null ? failure : translated;
    }

    private static Optional<Outage> temporaryOutage(Throwable classified) {
        return TemporaryFailure.of(classified).map(kind -> new Outage(kind.typeOf(classified), sqlState(classified)));
    }

    private static Outage outageOf(Throwable classified) {
        return temporaryOutage(classified)
                .orElseGet(() -> new Outage(classified.getClass().getSimpleName(), sqlState(classified)));
    }

    private static String sqlState(Throwable failure) {
        return SqlStates.deepestOrNone(failure);
    }

    public record Outage(String type, String sqlState) {
    }
}

package ru.projectx.datareference.service.inbound;

import org.springframework.stereotype.Component;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.service.database.DatabaseOutages;
import ru.projectx.datareference.service.database.SqlStates;

@Component
public class ParseFailures {

    private final DatabaseOutages outages;

    public ParseFailures(DatabaseOutages outages) {
        this.outages = outages;
    }

    public ParseFailure classify(RuntimeException failure) {
        return outages.recognize(failure)
                .<ParseFailure>map(ParseFailures::temporary)
                .or(() -> outages.describe(failure).map(ParseFailures::rejectedByDatabase))
                .orElseGet(() -> permanent(failure));
    }

    private static ParseFailure temporary(DatabaseOutages.Outage outage) {
        return new ParseFailure.Temporary(
                "временный отказ базы данных: %s, SQLSTATE %s".formatted(outage.type(), outage.sqlState()),
                outage.type(), outage.sqlState());
    }

    private static ParseFailure rejectedByDatabase(DatabaseOutages.Outage outage) {
        return new ParseFailure.Permanent(
                "база данных отклонила запись: %s, SQLSTATE %s".formatted(outage.type(), outage.sqlState()),
                outage.type(), outage.sqlState());
    }

    private static ParseFailure permanent(RuntimeException failure) {
        String type = failure.getClass().getSimpleName();
        String reason = failure instanceof OneCFieldException own
                ? own.getMessage()
                : "непредвиденная ошибка разбора: " + type;
        return new ParseFailure.Permanent(reason, type, SqlStates.deepestOrNone(failure));
    }
}

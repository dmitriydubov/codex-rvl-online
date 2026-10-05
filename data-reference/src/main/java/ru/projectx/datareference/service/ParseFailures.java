package ru.projectx.datareference.service;

import org.springframework.stereotype.Component;
import ru.projectx.datareference.exception.OneCFieldException;

/**
 * Решает, что делать с сообщением 1С:УСК, разбор которого закончился ошибкой: повторить позже или сразу
 * отклонить.
 *
 * <p>Повторяется только временный отказ базы. Какой отказ временный, решает {@link DatabaseOutages}, тот же
 * класс, по которому REST отвечает 503: перечень временных отказов один на весь сервис. Всё остальное при
 * повторе даст тот же отказ. Это сообщение, которое нарушает контракт, значение, которое база не принимает,
 * и ошибка в коде разбора. Такое сообщение отклоняется сразу и ждёт, пока его разберёт дежурный.
 */
@Component
public class ParseFailures {

    private final DatabaseOutages outages;

    public ParseFailures(DatabaseOutages outages) {
        this.outages = outages;
    }

    /**
     * Классифицирует ошибку разбора и составляет причину для строки сырья.
     *
     * @param failure ошибка, с которой закончилась попытка разбора
     * @return временный отказ базы или постоянная ошибка
     */
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

    /**
     * Текст своей ошибки разбора называет поле, но не значение, и становится причиной как есть. У прочих ошибок
     * текст чужой, поэтому в причину идёт только тип.
     */
    private static ParseFailure permanent(RuntimeException failure) {
        String type = failure.getClass().getSimpleName();
        String reason = failure instanceof OneCFieldException own
                ? own.getMessage()
                : "непредвиденная ошибка разбора: " + type;
        return new ParseFailure.Permanent(reason, type, SqlStates.deepestOrNone(failure));
    }
}

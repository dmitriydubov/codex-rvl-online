package ru.projectx.contracts.exception;

/**
 * Справочник не дал ответа, которому можно верить: не ответил вовремя, отказал по недоступности своей базы или
 * вернул неожиданный ответ. Ошибка временная: задача выпуска остаётся в текущем состоянии и повторяется позже.
 */
public class ReferenceUnavailableException extends RuntimeException {

    /** Чем закончилось обращение; в журнал уходит только это, без тела ответа. */
    public enum Reason {
        /** Справочник ответил 503: его база недоступна или не ответила вовремя. */
        SERVICE_UNAVAILABLE,
        /** Ответ не пришёл за отведённое время. */
        TIMEOUT,
        /** Соединение не установилось или оборвалось. */
        CONNECTION_FAILED,
        /** Статус или тело ответа не из контракта справочника, например страница ошибки прокси. */
        UNEXPECTED_RESPONSE,
        /** Полис, о котором сообщила 1С:УСК, ещё не дошёл до справочника за отведённые попытки. */
        POLICY_NOT_RECEIVED
    }

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public ReferenceUnavailableException(Reason reason) {
        super("справочник недоступен: " + reason);
        this.reason = reason;
    }

    public ReferenceUnavailableException(Reason reason, Throwable cause) {
        super("справочник недоступен: " + reason, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}

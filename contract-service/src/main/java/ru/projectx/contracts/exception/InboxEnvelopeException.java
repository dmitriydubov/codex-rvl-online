package ru.projectx.contracts.exception;

public class InboxEnvelopeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InboxEnvelopeException(String reason) {
        super(reason);
    }

    public InboxEnvelopeException(String reason, Throwable cause) {
        super(reason, cause);
    }

    public static InboxEnvelopeException unusableField(String field) {
        return new InboxEnvelopeException("в конверте нет пригодного " + field);
    }

    public static InboxEnvelopeException unreadableBody(Throwable cause) {
        return new InboxEnvelopeException("тело не читается как JSON", cause);
    }
}

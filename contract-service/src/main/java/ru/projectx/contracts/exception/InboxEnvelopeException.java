package ru.projectx.contracts.exception;

public class InboxEnvelopeException extends RuntimeException {

    public static final String UNUSABLE_ENVELOPE = "unusable_envelope";
    public static final String UNREADABLE_BODY = "unreadable_body";

    private static final long serialVersionUID = 1L;

    private final String lossReason;

    public InboxEnvelopeException(String reason, String lossReason) {
        super(reason);
        this.lossReason = lossReason;
    }

    public InboxEnvelopeException(String reason, String lossReason, Throwable cause) {
        super(reason, cause);
        this.lossReason = lossReason;
    }

    public static InboxEnvelopeException unusableField(String field) {
        return new InboxEnvelopeException("в конверте нет пригодного " + field, UNUSABLE_ENVELOPE);
    }

    public static InboxEnvelopeException unreadableBody(Throwable cause) {
        return new InboxEnvelopeException("тело не читается как JSON", UNREADABLE_BODY, cause);
    }

    public String lossReason() {
        return lossReason;
    }
}

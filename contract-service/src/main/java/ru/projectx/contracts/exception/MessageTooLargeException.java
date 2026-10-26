package ru.projectx.contracts.exception;

public class MessageTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long limit;
    private final int documents;

    public MessageTooLargeException(long size, long limit, int documents) {
        super("сообщение в " + size + " байт больше предела " + limit + " байт, вложений — " + documents);
        this.limit = limit;
        this.documents = documents;
    }

    public long limit() {
        return limit;
    }

    public int documents() {
        return documents;
    }
}

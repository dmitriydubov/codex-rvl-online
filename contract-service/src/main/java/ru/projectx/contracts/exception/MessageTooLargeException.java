package ru.projectx.contracts.exception;

public class MessageTooLargeException extends SubmissionRefusedException {

    private static final long serialVersionUID = 1L;

    public MessageTooLargeException(long size, long limit, int documents, String errorCode, String errorMessage) {
        super("сообщение в " + size + " байт больше предела " + limit + " байт, вложений — " + documents,
                errorCode, errorMessage);
    }
}

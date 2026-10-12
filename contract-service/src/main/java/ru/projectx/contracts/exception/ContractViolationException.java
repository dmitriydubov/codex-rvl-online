package ru.projectx.contracts.exception;

public class ContractViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ContractViolationException(String reason) {
        super(reason);
    }

    public ContractViolationException(String reason, Throwable cause) {
        super(reason, cause);
    }

    public static ContractViolationException missingField(String path) {
        return new ContractViolationException("нет обязательного поля " + path);
    }

    public static ContractViolationException invalidValue(String path) {
        return new ContractViolationException("недопустимое значение поля " + path);
    }

    public static ContractViolationException invalidValue(String path, Throwable cause) {
        return new ContractViolationException("недопустимое значение поля " + path, cause);
    }

    public static ContractViolationException unreadableBody(Throwable cause) {
        return new ContractViolationException("тело не читается как JSON", cause);
    }
}

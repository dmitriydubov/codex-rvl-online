package ru.projectx.contracts.exception;

public class PayerEmailRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PayerEmailRequiredException() {
        super("в акцепте нет адреса электронной почты плательщика, а оплата включена");
    }
}

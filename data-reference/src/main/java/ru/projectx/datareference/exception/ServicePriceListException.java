package ru.projectx.datareference.exception;

import java.io.Serial;
import java.util.UUID;

public abstract sealed class ServicePriceListException extends RuntimeException
        permits AmbiguousPriceException, PriceNotCoveredException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String code;
    private final UUID serviceId;

    ServicePriceListException(String code, String message, UUID serviceId) {
        super(message);
        this.code = code;
        this.serviceId = serviceId;
    }

    public String code() {
        return code;
    }

    public UUID serviceId() {
        return serviceId;
    }
}

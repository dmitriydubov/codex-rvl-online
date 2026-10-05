package ru.projectx.datareference.exception;

import java.io.Serial;
import java.util.UUID;

/**
 * Прайс услуги в 1С:УСК не позволяет посчитать её стоимость за период: на одни сутки действуют две строки прайса
 * или на какие-то сутки строки нет. Это ошибка данных в 1С, а не вызывающего. Исключение несёт код ошибки и
 * идентификатор услуги, прайс которой надо исправить; что именно не так, хранит конкретный вид отказа.
 */
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

    /** Услуга, прайс которой надо исправить в 1С:УСК. */
    public UUID serviceId() {
        return serviceId;
    }
}

package ru.projectx.datareference.controller;

import org.springframework.core.Ordered;

/**
 * Порядок обработчиков ошибок REST. Spring берёт первый обработчик с подходящим методом и не ищет более точного,
 * поэтому обработчик любого исключения стоит последним, иначе отказы справочника и запроса стали бы ответом 500.
 */
final class ErrorAdviceOrder {

    /** Отказы справочника: 404, 409 и 422. */
    static final int REFERENCE = 1;
    /** Ошибки вызывающего: 400, 404 на чужой путь, 405 и 406. */
    static final int REQUEST = 2;
    /** Сбой сервиса: 503 и 500. */
    static final int SERVER_FAILURE = Ordered.LOWEST_PRECEDENCE;

    private ErrorAdviceOrder() {
    }
}

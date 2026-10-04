package ru.projectx.datareference.controller;

import org.springframework.core.Ordered;

/**
 * Порядок, в котором Spring опрашивает обработчики ошибок REST. Spring берёт первый обработчик, у которого
 * нашёлся подходящий метод, и не ищет среди остальных более точный. Поэтому обработчик любого исключения
 * стоит последним, иначе он перехватил бы отказы справочника и запроса и ответил бы на них 500.
 * Справочник и запрос ловят разные исключения, и их взаимный порядок задан только для определённости.
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

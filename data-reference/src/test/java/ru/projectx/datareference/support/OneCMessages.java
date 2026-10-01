package ru.projectx.datareference.support;

import java.util.UUID;

/** Сообщения 1С для тестов: конверт {@code {data, metadata}} вокруг полезной нагрузки. */
public final class OneCMessages {

    /**
     * GUID вида деятельности «РВЛ» тестового контура — тот же, что в
     * {@code application.yml}. Санаторий без него в справочник не попадает.
     */
    public static final String RVL_ACTIVITY = "f3876c53-c7d8-4620-b364-9538164ba5d4";

    /**
     * Оборачивает {@code data} в конверт источника.
     *
     * <p>{@code metadata} здесь формальна: к моменту разбора из неё уже всё взято —
     * {@code message-id} стал ключом строки сырья ещё при приёме. Но выкидывать её
     * из тестовых сообщений нельзя: разбор обязан работать с тем конвертом, который
     * реально лежит в таблице.
     */
    public static String envelope(String data) {
        return """
                {"data":%s,\
                "metadata":{"message-id":"9f1c0f2e-0000-4000-8000-00000000000a","producer":"1c-usk"}}"""
                .formatted(data);
    }

    /**
     * Сообщение ТЗ-4 (МКБ-10) целиком, с заданным {@code message-id} — для тестов приёма
     * из очереди, где сообщения различает только он.
     */
    public static String mkb10Message(UUID messageId) {
        return """
                {"data":{"guid":"1a2b3c4d-0000-4000-8000-000000000001","code":"A00"},\
                "metadata":{"message-id":"%s","topic":"","producer":"1c-usk"}}"""
                .formatted(messageId);
    }

    /**
     * Сообщение ТЗ-3, из которого получается санаторий. Нужно почти всем потокам
     * со связями: договоры, программы и адреса цепляются именно к нему.
     */
    public static String sanatorium(UUID externalId, String name) {
        return """
                {"guid":"%s","thisIsLPU":true,"Наименование":"%s","ИНН":"7701234567",\
                "typesActivities":[{"guidTypeActivities":"%s"}]}"""
                .formatted(externalId, name, RVL_ACTIVITY);
    }

    private OneCMessages() {
    }
}

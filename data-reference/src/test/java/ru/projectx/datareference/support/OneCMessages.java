package ru.projectx.datareference.support;

import java.util.UUID;

public final class OneCMessages {

    public static final String RVL_ACTIVITY = "f3876c53-c7d8-4620-b364-9538164ba5d4";

    public static String envelope(String data) {
        return """
                {"data":%s,\
                "metadata":{"message-id":"9f1c0f2e-0000-4000-8000-00000000000a","producer":"1c-usk"}}"""
                .formatted(data);
    }

    public static String messageWithId(UUID messageId) {
        return """
                {"data":{"guid":"1a2b3c4d-0000-4000-8000-000000000001","code":"A00"},\
                "metadata":{"message-id":"%s","topic":"","producer":"1c-usk"}}"""
                .formatted(messageId);
    }

    public static String sanatorium(UUID externalId, String name) {
        return """
                {"guid":"%s","thisIsLPU":true,"Наименование":"%s","ИНН":"7701234567",\
                "typesActivities":[{"guidTypeActivities":"%s"}]}"""
                .formatted(externalId, name, RVL_ACTIVITY);
    }

    public static String sanatoriumWithoutRvl(UUID externalId, String name) {
        return """
                {"guid":"%s","thisIsLPU":true,"Наименование":"%s","ИНН":"7701234567","typesActivities":[]}"""
                .formatted(externalId, name);
    }

    private OneCMessages() {
    }
}

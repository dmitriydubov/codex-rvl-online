package ru.projectx.contracts.service.integration;

import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;

final class UnstorableText {

    static final String REASON = "в теле символ, который база не хранит: нулевой или непарный суррогат";

    private UnstorableText() {
    }

    static boolean present(JsonNode node) {
        if (node.isString()) {
            return unstorable(node.stringValue());
        }
        return node.isObject() ? presentInProperties(node) : presentInItems(node);
    }

    private static boolean presentInProperties(JsonNode node) {
        return node.propertyStream().anyMatch(property -> unstorable(property.getKey()) || present(property.getValue()));
    }

    private static boolean presentInItems(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false).anyMatch(UnstorableText::present);
    }

    private static boolean unstorable(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (unstorableAt(text, index)) {
                return true;
            }
        }
        return false;
    }

    private static boolean unstorableAt(String text, int index) {
        return text.charAt(index) == '\u0000' || highWithoutLow(text, index) || lowWithoutHigh(text, index);
    }

    private static boolean highWithoutLow(String text, int index) {
        return Character.isHighSurrogate(text.charAt(index))
                && (index + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(index + 1)));
    }

    private static boolean lowWithoutHigh(String text, int index) {
        return Character.isLowSurrogate(text.charAt(index))
                && (index == 0 || !Character.isHighSurrogate(text.charAt(index - 1)));
    }
}

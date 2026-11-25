package ru.projectx.contracts.acceptance;

import tools.jackson.databind.node.ObjectNode;

final class OptionalFieldValues {

    private OptionalFieldValues() {
    }

    static void put(ObjectNode body, String path, String kind) {
        String pointer = "/data/" + path.replace('.', '/').replace('[', '/').replace("]", "");
        int lastSlash = pointer.lastIndexOf('/');
        if (!(body.at(pointer.substring(0, lastSlash)) instanceof ObjectNode parent)) {
            throw new AssertionError("нет объекта для поля " + path);
        }
        String name = pointer.substring(lastSlash + 1);
        switch (kind) {
            case "число" -> parent.put(name, 42);
            case "логическое значение" -> parent.put(name, false);
            case "массив" -> parent.putArray(name);
            case "объект" -> parent.putObject(name).put("fio", "СТЕНД-К15-ФИКТИВНОЕ-ЗНАЧЕНИЕ");
            case "ключ отсутствует" -> parent.remove(name);
            case "JSON null" -> parent.putNull(name);
            case "строка" -> parent.put(name, "СТЕНД-ЛМ-0002");
            case "пустая строка" -> parent.put(name, "");
            case "строка из пробелов" -> parent.put(name, "   ");
            default -> throw new AssertionError("неизвестный вид поля: " + kind);
        }
    }
}

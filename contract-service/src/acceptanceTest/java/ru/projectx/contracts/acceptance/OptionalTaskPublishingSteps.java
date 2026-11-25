package ru.projectx.contracts.acceptance;

import io.cucumber.java.ru.Когда;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.node.ObjectNode;

public class OptionalTaskPublishingSteps {

    private final TaskMessages messages;
    private final TaskPublishingSteps publishing;

    public OptionalTaskPublishingSteps(TaskMessages messages, TaskPublishingSteps publishing) {
        this.messages = messages;
        this.publishing = publishing;
    }

    @Когда("сервис Заявок публикует задачу на выпуск {string} по заявке {string} версии {int}, задав полю {string} вид {string}:")
    public void taskWithOptionalField(String messageId, String requestId, int version, String path, String kind,
            List<Map<String, String>> participants) {
        ObjectNode body = messages.upserted(messageId, requestId, version, participants);
        OptionalFieldValues.put(body, path, kind);
        publishing.publish(messageId, body);
    }

    @Когда("сервис Заявок публикует задачу на выпуск {string} по заявке {string} версии 1 по застрахованному {string} с доплатой 15.00 и указанной программой, задав вид {string} для полей:")
    public void taskWithOptionalPersonalFields(String messageId, String requestId, String insured, String kind,
            List<String> paths) {
        var participants = List.of(Map.of("участник", insured, "родственник", "нет", "доплата", "15.00"));
        ObjectNode body = messages.upserted(messageId, requestId, 1, participants);
        paths.forEach(path -> OptionalFieldValues.put(body, "participants[0]." + path, kind));
        publishing.publish(messageId, body);
    }

    @Когда("сервис Заявок публикует прекращение работы {string} по заявке {string} версии 2 с причиной INSURED_REFUSED, моментом {string} и полем cancel_comment вида {string}")
    public void cancellationWithOptionalComment(String messageId, String requestId, String moment, String kind) {
        ObjectNode body = messages.cancelled(messageId, requestId, 2,
                Map.of("причина", "INSURED_REFUSED", "аннулировано", moment));
        OptionalFieldValues.put(body, "cancel_comment", kind);
        publishing.publish(messageId, body);
    }
}

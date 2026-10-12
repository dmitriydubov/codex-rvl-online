package ru.projectx.contracts.acceptance;

import io.cucumber.spring.ScenarioScope;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class PublishedMessages {

    private final Map<String, byte[]> byMessageId = new HashMap<>();
    private byte @Nullable [] defectiveEnvelope;
    private byte @Nullable [] defectiveEnvelopeSource;

    void remember(String messageId, byte[] body) {
        byMessageId.putIfAbsent(messageId, body);
    }

    void rememberDefectiveEnvelope(byte[] body) {
        defectiveEnvelope = body;
    }

    void rememberDefectiveEnvelopeSource(byte[] intactBody) {
        defectiveEnvelopeSource = intactBody;
    }

    byte[] defectiveEnvelopeSource() {
        return Optional.ofNullable(defectiveEnvelopeSource)
                .orElseThrow(() -> new IllegalStateException("сообщение с дефектом конверта в сценарии не публиковалось"));
    }

    java.util.Set<String> messageIds() {
        return java.util.Set.copyOf(byMessageId.keySet());
    }

    byte[] of(String messageId) {
        return Optional.ofNullable(byMessageId.get(messageId))
                .orElseThrow(() -> new IllegalStateException("сообщение " + messageId + " в сценарии не публиковалось"));
    }

    byte[] defectiveEnvelope() {
        return Optional.ofNullable(defectiveEnvelope)
                .orElseThrow(() -> new IllegalStateException("сообщение с дефектом конверта в сценарии не публиковалось"));
    }
}

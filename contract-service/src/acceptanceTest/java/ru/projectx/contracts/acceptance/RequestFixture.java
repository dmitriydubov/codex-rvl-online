package ru.projectx.contracts.acceptance;

import io.cucumber.spring.ScenarioScope;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class RequestFixture {

    private final Map<String, Map<String, String>> requests = new HashMap<>();

    void define(Map<String, String> requisites) {
        requests.put(StepTables.required(requisites, "заявка"), Map.copyOf(requisites));
    }

    Map<String, String> of(String requestId) {
        return Optional.ofNullable(requests.get(requestId))
                .orElseThrow(() -> new IllegalStateException("заявка " + requestId + " не задана шагом «у сервиса Заявок есть заявка»"));
    }
}

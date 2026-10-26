package ru.projectx.contracts.acceptance;

import io.cucumber.spring.ScenarioScope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class ReceivedKmContractSubmissions extends ReceivedMessages {

    public ReceivedKmContractSubmissions() {
        super("передач договоров КМ");
    }
}

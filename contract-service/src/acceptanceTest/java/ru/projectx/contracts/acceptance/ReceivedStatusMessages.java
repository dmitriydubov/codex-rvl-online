package ru.projectx.contracts.acceptance;

import io.cucumber.spring.ScenarioScope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class ReceivedStatusMessages extends ReceivedMessages {

    public ReceivedStatusMessages() {
        super("сообщений о состоянии выпуска");
    }
}

package ru.projectx.contracts.acceptance;

import com.rabbitmq.client.GetResponse;
import io.cucumber.spring.ScenarioScope;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class ReceivedStatusMessages {

    private final List<GetResponse> received = new ArrayList<>();

    void add(List<GetResponse> more) {
        received.addAll(more);
    }

    int count() {
        return received.size();
    }

    List<GetResponse> all() {
        return List.copyOf(received);
    }

    GetResponse number(int ordinal) {
        if (ordinal < 1 || ordinal > received.size()) {
            throw new AssertionError("за сценарий получено " + received.size() + " сообщений о состоянии выпуска, "
                    + "сообщения №" + ordinal + " нет");
        }
        return received.get(ordinal - 1);
    }
}

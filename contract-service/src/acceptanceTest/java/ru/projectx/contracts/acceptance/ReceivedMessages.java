package ru.projectx.contracts.acceptance;

import com.rabbitmq.client.GetResponse;
import java.util.ArrayList;
import java.util.List;

abstract class ReceivedMessages {

    private final String label;
    private final List<GetResponse> received = new ArrayList<>();

    ReceivedMessages(String label) {
        this.label = label;
    }

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
            throw new AssertionError("за сценарий получено " + received.size() + " " + label + ", сообщения №"
                    + ordinal + " нет");
        }
        return received.get(ordinal - 1);
    }
}

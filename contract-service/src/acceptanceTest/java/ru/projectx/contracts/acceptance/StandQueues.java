package ru.projectx.contracts.acceptance;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StandQueues {

    private final List<StandQueue> queues;

    public StandQueues(List<StandQueue> queues) {
        this.queues = List.copyOf(queues);
    }

    StandQueue called(String alias) {
        return queues.stream().filter(queue -> queue.alias().equals(alias)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("стенд не знает очереди «" + alias + "»"));
    }
}

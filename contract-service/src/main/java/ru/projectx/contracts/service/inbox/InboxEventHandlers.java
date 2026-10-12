package ru.projectx.contracts.service.inbox;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.exception.ContractViolationException;

@Component
public class InboxEventHandlers {

    private final Map<String, InboxEventHandler> byEventType;

    public InboxEventHandlers(List<InboxEventHandler> handlers) {
        this.byEventType = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(InboxEventHandler::eventType, Function.identity()));
    }

    public InboxEventHandler forEventType(String eventType) {
        InboxEventHandler handler = byEventType.get(eventType);
        if (handler == null) {
            throw new ContractViolationException("неизвестный тип события " + eventType);
        }
        return handler;
    }
}

package ru.projectx.datareference.mapper;

import java.util.Optional;
import java.util.UUID;
import ru.projectx.datareference.entity.inbound.OneCStream;
import tools.jackson.databind.node.ObjectNode;

public interface StreamMapper {

    OneCStream stream();

    ImportResult apply(ObjectNode data);

    default Optional<UUID> recordOf(ObjectNode data) {
        return Optional.empty();
    }
}

package ru.projectx.datareference.service.policy;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.dto.PolicySearchResponse;

public interface PolicySearchReads {

    PolicySearchResponse search(UUID contractExternalId, String query, @Nullable Integer limit);
}

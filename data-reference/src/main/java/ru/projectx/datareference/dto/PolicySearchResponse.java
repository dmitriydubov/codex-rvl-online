package ru.projectx.datareference.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.Policy;

public record PolicySearchResponse(List<Item> items) {

    public static PolicySearchResponse of(List<Policy> policies) {
        return new PolicySearchResponse(policies.stream().map(Item::of).toList());
    }

    public record Item(
            UUID id,
            UUID externalId,
            String number,
            String lastName,
            String firstName,
            @Nullable String middleName) {

        static Item of(Policy policy) {
            Policy.Details details = policy.details();
            return new Item(policy.id(), policy.externalId(), details.number(),
                    details.lastName(), details.firstName(), details.middleName());
        }
    }
}

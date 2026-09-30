package ru.projectx.datareference.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.Policy;

/**
 * Подсказка для поля «найти сотрудника» (R-04): ФИО и номер, чтобы выбрать из короткого
 * списка. Дата рождения и e-mail сюда не входят намеренно — e-mail только в R-05,
 * а различение тёзок по дате рождения аналитик оставил открытым.
 */
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
            return new Item(policy.id(), policy.externalId(), policy.number(),
                    policy.lastName(), policy.firstName(), policy.middleName());
        }
    }
}

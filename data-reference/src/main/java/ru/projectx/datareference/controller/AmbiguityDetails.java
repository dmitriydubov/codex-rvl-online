package ru.projectx.datareference.controller;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import ru.projectx.datareference.dto.ErrorResponse;
import ru.projectx.datareference.exception.AmbiguousReferenceException;

/**
 * Перечень конфликтующих записей для ответа 409. Каждая запись становится элементом {@code details} того
 * вида, который соответствует коду ошибки: полис называется полем {@code policy_id}, договор ЛПУ
 * (лечебно-профилактического учреждения) называется полем {@code lpu_contract_id}.
 */
final class AmbiguityDetails {

    private AmbiguityDetails() {
    }

    static List<ErrorResponse.Detail> of(AmbiguousReferenceException ambiguous) {
        Function<UUID, ErrorResponse.Detail> detail = switch (ambiguous.candidateKind()) {
            case POLICY -> ErrorResponse.PolicyCandidate::new;
            case LPU_CONTRACT -> ErrorResponse.LpuContractCandidate::new;
        };
        return ambiguous.candidateIds().stream().map(detail).toList();
    }
}

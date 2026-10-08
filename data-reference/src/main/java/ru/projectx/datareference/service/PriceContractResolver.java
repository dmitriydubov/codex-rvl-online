package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.IncompleteReferenceException;
import ru.projectx.datareference.repository.LpuContractRepository;

/**
 * Выбирает прайсовый договор лечебно-профилактического учреждения (ЛПУ) по одному правилу для каталога, карточки,
 * услуг и расчёта стоимости. Сначала ищется действующее допсоглашение со своими ценами, без него главный договор;
 * несколько кандидатов дают неоднозначность. Главный договор и допсоглашения в 1С:УСК действуют одновременно.
 */
@Component
public class PriceContractResolver {

    private final LpuContractRepository lpuContracts;

    public PriceContractResolver(LpuContractRepository lpuContracts) {
        this.lpuContracts = lpuContracts;
    }

    /** Ответ по одному учреждению подменить нечем, поэтому неоднозначность даёт 409, а отсутствие договора 422. */
    public PriceContract resolve(UUID lpuId, LocalDate today) {
        return switch (resolveAll(List.of(lpuId), today).get(lpuId)) {
            case Resolution.Found found -> found.priceContract();
            case Resolution.Ambiguous ambiguous ->
                    throw AmbiguousReferenceException.priceContract(lpuId, ambiguous.lpuContractIds());
            case Resolution.None none -> throw IncompleteReferenceException.noActivePriceContract(lpuId);
        };
    }

    /** Исход по каждому учреждению одним запросом; что делать с неоднозначным или без договора, решает вызывающий. */
    public Map<UUID, Resolution> resolveAll(Collection<UUID> lpuIds, LocalDate today) {
        if (lpuIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<LpuContract>> activeByLpu = lpuContracts.findActiveByLpuIdIn(lpuIds, today).stream()
                .collect(Collectors.groupingBy(contract -> contract.lpu().id()));
        return lpuIds.stream()
                .distinct()
                .collect(Collectors.toMap(Function.identity(),
                        lpuId -> choose(activeByLpu.getOrDefault(lpuId, List.of()))));
    }

    private static Resolution choose(List<LpuContract> active) {
        List<LpuContract> candidates = Optional.of(active.stream().filter(PriceContractResolver::hasOwnPrices).toList())
                .filter(Predicate.not(List::isEmpty))
                .orElseGet(() -> active.stream().filter(PriceContractResolver::isMain).toList());
        return switch (candidates.size()) {
            case 0 -> new Resolution.None();
            case 1 -> new Resolution.Found(PriceContract.of(candidates.getFirst()));
            default -> new Resolution.Ambiguous(candidates.stream().map(LpuContract::id).toList());
        };
    }

    private static boolean hasOwnPrices(LpuContract contract) {
        return !isMain(contract) && !contract.usePricesMainContract();
    }

    private static boolean isMain(LpuContract contract) {
        return contract.mainContractExternalId() == null;
    }

    /** Исход выбора прайсового договора по одному учреждению. */
    public sealed interface Resolution {

        record Found(PriceContract priceContract) implements Resolution {
        }

        /** Перечень кандидатов нужен, чтобы исправить данные на стороне 1С. */
        record Ambiguous(List<UUID> lpuContractIds) implements Resolution {
        }

        /** Действующих договоров нет либо все они берут цены с главного, которого нет. */
        record None() implements Resolution {
        }
    }
}

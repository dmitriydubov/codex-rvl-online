package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.IncompleteReferenceException;
import ru.projectx.datareference.repository.LpuContractRepository;

/**
 * Базовая логика B из ТЗ на API: прайсовый договор ЛПУ — тот, по которому берутся услуги
 * и цены. Одна реализация на R-01, R-02, R-03 и R-06.
 *
 * <p>Кандидаты — договоры ЛПУ, действующие на сегодня. Среди них ищется допсоглашение
 * со своими ценами: {@code main_contract_external_id} заполнен и
 * {@code use_prices_main_contract = false}. Ровно одно — оно прайсовое. Нет ни одного —
 * прайсовый договор главный, у которого {@code main_contract_external_id} пуст. Два
 * и более кандидата на любом шаге — неоднозначность; ни одного — договора нет.
 * Допсоглашение, берущее цены с главного договора, в отборе не участвует вовсе.
 *
 * <p>Главный договор и допсоглашения действуют одновременно, это норма источника (В-5);
 * правило «не более одного активного» — остаток v1 и здесь не применяется.
 *
 * <p>Выбор сделан на Java поверх одного запроса на все учреждения: правило читается
 * сверху вниз ровно как в ТЗ, а кандидатов у одного ЛПУ единицы.
 */
@Component
public class PriceContractResolver {

    private final LpuContractRepository lpuContracts;

    public PriceContractResolver(LpuContractRepository lpuContracts) {
        this.lpuContracts = lpuContracts;
    }

    /** Точечные ручки (R-02, R-03, R-06): ответ по одному ЛПУ, и подменить его нечем — отказ. */
    public PriceContract resolve(UUID lpuId, LocalDate today) {
        return switch (resolveAll(List.of(lpuId), today).get(lpuId)) {
            case Resolution.Found found -> found.priceContract();
            case Resolution.Ambiguous ambiguous ->
                    throw AmbiguousReferenceException.priceContract(lpuId, ambiguous.lpuContractIds());
            case Resolution.None none -> throw IncompleteReferenceException.noActivePriceContract(lpuId);
        };
    }

    /** Каталог (R-01): исход по каждому из запрошенных учреждений, что с ним делать — решает вызывающий. */
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
        List<LpuContract> supplementsWithOwnPrices = active.stream()
                .filter(contract -> contract.mainContractExternalId() != null && !contract.usePricesMainContract())
                .toList();
        List<LpuContract> candidates = supplementsWithOwnPrices.isEmpty()
                ? active.stream().filter(contract -> contract.mainContractExternalId() == null).toList()
                : supplementsWithOwnPrices;
        return switch (candidates.size()) {
            case 0 -> new Resolution.None();
            case 1 -> new Resolution.Found(PriceContract.of(candidates.getFirst()));
            default -> new Resolution.Ambiguous(candidates.stream().map(LpuContract::id).toList());
        };
    }

    /** Исход логики B по одному учреждению. */
    public sealed interface Resolution {

        record Found(PriceContract priceContract) implements Resolution {
        }

        /** Кандидатов больше одного — перечень их {@code id}, по нему разбор на стороне 1С. */
        record Ambiguous(List<UUID> lpuContractIds) implements Resolution {
        }

        /** Действующих договоров нет либо все они берут цены с главного, которого нет. */
        record None() implements Resolution {
        }
    }
}

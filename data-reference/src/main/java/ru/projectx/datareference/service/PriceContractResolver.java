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
 * Выбирает прайсовый договор ЛПУ (лечебно-профилактического учреждения): договор, по которому берутся услуги
 * учреждения и их цены. Правило одно для каталога учреждений, карточки, перечня услуг и расчёта стоимости услуги
 * за период.
 *
 * <p>Кандидаты — договоры ЛПУ, действующие на сегодня. Среди них ищется допсоглашение со своими ценами: у него
 * заполнена ссылка на главный договор ({@code main_contract_external_id}), а цены главного договора оно не берёт
 * ({@code use_prices_main_contract = false}). Если такое допсоглашение одно, прайсовое оно. Если его нет,
 * прайсовым становится главный договор, у которого ссылки на главный нет. Два и более кандидата на любом шаге —
 * неоднозначность, ни одного — договора нет. Допсоглашение, которое берёт цены с главного договора, в выборе
 * не участвует вовсе.
 *
 * <p>Главный договор и его допсоглашения действуют одновременно, в 1С:УСК это норма. Поэтому правила «у учреждения
 * не больше одного действующего договора» здесь нет (журнал решений по REST, правило В-5).
 *
 * <p>Выбор сделан на Java поверх одного запроса на все учреждения: так правило читается сверху вниз, а
 * кандидатов у одного учреждения единицы.
 */
@Component
public class PriceContractResolver {

    private final LpuContractRepository lpuContracts;

    public PriceContractResolver(LpuContractRepository lpuContracts) {
        this.lpuContracts = lpuContracts;
    }

    /**
     * Прайсовый договор одного учреждения для карточки, перечня услуг и расчёта стоимости. Ответ идёт по одному
     * учреждению, и подменить его нечем, поэтому неоднозначность даёт отказ 409, а отсутствие договора — 422.
     */
    public PriceContract resolve(UUID lpuId, LocalDate today) {
        return switch (resolveAll(List.of(lpuId), today).get(lpuId)) {
            case Resolution.Found found -> found.priceContract();
            case Resolution.Ambiguous ambiguous ->
                    throw AmbiguousReferenceException.priceContract(lpuId, ambiguous.lpuContractIds());
            case Resolution.None none -> throw IncompleteReferenceException.noActivePriceContract(lpuId);
        };
    }

    /**
     * Исход выбора по каждому из запрошенных учреждений для каталога. Что делать с неоднозначным учреждением или
     * учреждением без договора, решает вызывающий.
     */
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

    /** Допсоглашение со своими ценами: ссылается на главный договор и цены с него не берёт. */
    private static boolean hasOwnPrices(LpuContract contract) {
        return !isMain(contract) && !contract.usePricesMainContract();
    }

    private static boolean isMain(LpuContract contract) {
        return contract.mainContractExternalId() == null;
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

package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceCaches;
import ru.projectx.datareference.dto.LpuCatalogResponse;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.service.PriceContractResolver.Resolution;

/**
 * Каталог ЛПУ (лечебно-профилактических учреждений) для личного кабинета кадровика по договору страхования.
 *
 * <p>Отбор идёт так. Действующие полисы договора дают программы восстановительного лечения; перечни этих программ
 * дают учреждения, но только строками с видом обращения РВЛ. Дальше по каждому учреждению выбирается прайсовый
 * договор, одним запросом на всех. Учреждение без действующего договора в каталог не попадает. Учреждение с
 * неоднозначным прайсовым договором уходит в {@code skipped} и в журнал: одна кривая строка в 1С не должна класть
 * каталог целиком для всех пользователей договора. В точечных ручках (карточка, услуги) то же — отказ {@code 409}
 * или {@code 422}: там ответ по одному ЛПУ, и подменить его нечем.
 *
 * <p>Каталог кэшируется по сегодняшнему дню контура и договору. Поэтому предупреждение о неоднозначном договоре
 * пишется не на каждый запрос, а когда каталог считается заново: после сброса кэша или по истечении срока жизни.
 */
@Service
public class LpuCatalogService implements LpuCatalogReads {

    private static final Logger log = LoggerFactory.getLogger(LpuCatalogService.class);

    private final InsuranceContractRepository insuranceContracts;
    private final PolicyRepository policies;
    private final LpuRepository lpus;
    private final PriceContractResolver priceContracts;
    private final RvlAppealTypes rvlAppealTypes;
    private final ReferenceCalendar calendar;

    public LpuCatalogService(
            InsuranceContractRepository insuranceContracts,
            PolicyRepository policies,
            LpuRepository lpus,
            PriceContractResolver priceContracts,
            RvlAppealTypes rvlAppealTypes,
            ReferenceCalendar calendar) {

        this.insuranceContracts = insuranceContracts;
        this.policies = policies;
        this.lpus = lpus;
        this.priceContracts = priceContracts;
        this.rvlAppealTypes = rvlAppealTypes;
        this.calendar = calendar;
    }

    @Override
    @Cacheable(cacheNames = ReferenceCaches.LPU_CATALOG, keyGenerator = ReferenceCaches.TODAY_KEY)
    @RestReadTransaction
    public LpuCatalogResponse catalog(UUID contractExternalId) {
        insuranceContracts.findByExternalIdAndArchiveFalse(contractExternalId)
                .orElseThrow(() -> ReferenceNotFoundException.insuranceContractByExternalId(contractExternalId));
        LocalDate today = calendar.today();
        List<Lpu> candidates = rvlLpuOf(contractExternalId, today);
        Map<UUID, Resolution> priceContractByLpu =
                priceContracts.resolveAll(candidates.stream().map(Lpu::id).toList(), today);

        List<LpuCatalogResponse.Item> items = candidates.stream()
                .filter(lpu -> priceContractByLpu.get(lpu.id()) instanceof Resolution.Found)
                .map(LpuCatalogResponse.Item::of)
                .toList();
        return new LpuCatalogResponse(items, skippedOf(candidates, priceContractByLpu));
    }

    /**
     * Программы РВЛ действующих полисов договора и учреждения из их перечней со строкой
     * по виду обращения РВЛ. Сроки строки перечня в отбор не входят.
     */
    private List<Lpu> rvlLpuOf(UUID contractExternalId, LocalDate today) {
        List<UUID> programs = policies.findRvlProgramExternalIdsOfActivePolicies(contractExternalId, today);
        Set<UUID> appealTypes = rvlAppealTypes.externalIds();
        if (programs.isEmpty() || appealTypes.isEmpty()) {
            return List.of();
        }
        return lpus.findRvlCatalog(programs, appealTypes);
    }

    /** Обычный цикл, а не стрим: каждое исключённое учреждение ещё и пишется в журнал. */
    private static List<LpuCatalogResponse.Skipped> skippedOf(List<Lpu> candidates, Map<UUID, Resolution> byLpu) {
        List<LpuCatalogResponse.Skipped> skipped = new ArrayList<>();
        for (Lpu lpu : candidates) {
            if (byLpu.get(lpu.id()) instanceof Resolution.Ambiguous ambiguous) {
                skipped.add(skip(lpu, ambiguous));
            }
        }
        return List.copyOf(skipped);
    }

    /**
     * Неоднозначный прайсовый договор: причина уходит вызывающему в {@code skipped} тем же кодом,
     * каким ответила бы точечная ручка, и в журнал — по таким записям собирается очередь
     * на исправление на стороне 1С.
     */
    private static LpuCatalogResponse.Skipped skip(Lpu lpu, Resolution.Ambiguous ambiguous) {
        log.warn("каталог: ЛПУ {} исключено, кандидатов на прайсовый договор больше одного: {}",
                lpu.id(), ambiguous.lpuContractIds());
        return new LpuCatalogResponse.Skipped(lpu.id(), AmbiguousReferenceException.AMBIGUOUS_PRICE_CONTRACT);
    }
}

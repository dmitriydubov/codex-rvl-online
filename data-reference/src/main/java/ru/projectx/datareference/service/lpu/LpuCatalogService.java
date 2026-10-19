package ru.projectx.datareference.service.lpu;

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
import ru.projectx.datareference.entity.catalog.Lpu;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.repository.catalog.LpuRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.service.lpu.PriceContractResolver.Resolution;
import ru.projectx.datareference.service.insurancecontract.ActiveInsuranceContracts;
import ru.projectx.datareference.service.rest.ReferenceCalendar;
import ru.projectx.datareference.service.rest.RestReadTransaction;

@Service
public class LpuCatalogService implements LpuCatalogReads {

    private static final Logger log = LoggerFactory.getLogger(LpuCatalogService.class);

    private final ActiveInsuranceContracts activeContracts;
    private final PolicyRepository policies;
    private final LpuRepository lpus;
    private final PriceContractResolver priceContracts;
    private final RvlAppealTypes rvlAppealTypes;
    private final ReferenceCalendar calendar;

    public LpuCatalogService(
            ActiveInsuranceContracts activeContracts,
            PolicyRepository policies,
            LpuRepository lpus,
            PriceContractResolver priceContracts,
            RvlAppealTypes rvlAppealTypes,
            ReferenceCalendar calendar) {

        this.activeContracts = activeContracts;
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
        LocalDate today = calendar.today();
        activeContracts.requireActive(contractExternalId, today);
        List<Lpu> candidates = rvlLpuOf(contractExternalId, today);
        Map<UUID, Resolution> priceContractByLpu =
                priceContracts.resolveAll(candidates.stream().map(Lpu::id).toList(), today);

        List<LpuCatalogResponse.Item> items = candidates.stream()
                .filter(lpu -> priceContractByLpu.get(lpu.id()) instanceof Resolution.Found)
                .map(LpuCatalogResponse.Item::of)
                .toList();
        return new LpuCatalogResponse(items, skippedOf(candidates, priceContractByLpu));
    }

    private List<Lpu> rvlLpuOf(UUID contractExternalId, LocalDate today) {
        List<UUID> programs = policies.findRvlProgramExternalIdsOfActivePolicies(contractExternalId, today);
        Set<UUID> appealTypes = rvlAppealTypes.externalIds();
        if (programs.isEmpty() || appealTypes.isEmpty()) {
            return List.of();
        }
        return lpus.findRvlCatalog(programs, appealTypes);
    }

    private static List<LpuCatalogResponse.Skipped> skippedOf(List<Lpu> candidates, Map<UUID, Resolution> byLpu) {
        List<LpuCatalogResponse.Skipped> skipped = new ArrayList<>();
        for (Lpu lpu : candidates) {
            if (byLpu.get(lpu.id()) instanceof Resolution.Ambiguous ambiguous) {
                skipped.add(skip(lpu, ambiguous));
            }
        }
        return List.copyOf(skipped);
    }

    private static LpuCatalogResponse.Skipped skip(Lpu lpu, Resolution.Ambiguous ambiguous) {
        log.warn("каталог: ЛПУ {} исключено, кандидатов на прайсовый договор больше одного: {}",
                lpu.id(), ambiguous.lpuContractIds());
        return new LpuCatalogResponse.Skipped(lpu.id(), AmbiguousReferenceException.AMBIGUOUS_PRICE_CONTRACT);
    }
}

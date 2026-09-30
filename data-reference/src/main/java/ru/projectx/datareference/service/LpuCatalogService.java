package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.dto.LpuCardResponse;
import ru.projectx.datareference.dto.LpuCatalogResponse;
import ru.projectx.datareference.dto.LpuServicesResponse;
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.PriceListItem;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.repository.PriceListItemRepository;
import ru.projectx.datareference.service.PriceContractResolver.Resolution;

/**
 * Каталог ЛПУ для ЛК HR: список по договору (R-01), карточка (R-02), услуги и цены (R-03).
 *
 * <p>Порядок отбора каталога — как в ТЗ на API. Базовая логика A даёт программы РВЛ
 * действующих полисов договора; перечни этих программ дают учреждения, но только
 * строками с видом обращения РВЛ (В-6); дальше базовая логика B по каждому учреждению,
 * одним запросом на всех. Учреждение без действующего договора в каталог не попадает.
 * Учреждение с неоднозначным прайсовым договором уходит в {@code skipped} и в журнал:
 * одна кривая строка в 1С не должна класть каталог целиком для всех пользователей
 * договора (В-6). В точечных ручках то же — отказ {@code 409} или {@code 422}: там
 * ответ по одному ЛПУ, и подменить его нечем.
 *
 * <p>Транзакция здесь и {@code readOnly}; ответы собираются внутри неё, за её границей
 * ленивые связи не догрузятся.
 */
@Service
public class LpuCatalogService {

    private static final Logger log = LoggerFactory.getLogger(LpuCatalogService.class);

    private final InsuranceContractRepository insuranceContracts;
    private final PolicyRepository policies;
    private final LpuRepository lpus;
    private final ContragentServiceRepository contragentServices;
    private final PriceListItemRepository priceListItems;
    private final PriceContractResolver priceContracts;
    private final RvlAppealTypes rvlAppealTypes;
    private final ReferenceCalendar calendar;

    public LpuCatalogService(
            InsuranceContractRepository insuranceContracts,
            PolicyRepository policies,
            LpuRepository lpus,
            ContragentServiceRepository contragentServices,
            PriceListItemRepository priceListItems,
            PriceContractResolver priceContracts,
            RvlAppealTypes rvlAppealTypes,
            ReferenceCalendar calendar) {

        this.insuranceContracts = insuranceContracts;
        this.policies = policies;
        this.lpus = lpus;
        this.contragentServices = contragentServices;
        this.priceListItems = priceListItems;
        this.priceContracts = priceContracts;
        this.rvlAppealTypes = rvlAppealTypes;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public LpuCatalogResponse catalog(UUID contractExternalId) {
        insuranceContracts.findByExternalIdAndArchiveFalse(contractExternalId)
                .orElseThrow(() -> ReferenceNotFoundException.insuranceContractByExternalId(contractExternalId));
        LocalDate today = calendar.today();
        List<Lpu> candidates = rvlLpuOf(contractExternalId, today);
        Map<UUID, Resolution> priceContractByLpu =
                priceContracts.resolveAll(candidates.stream().map(Lpu::id).toList(), today);

        List<LpuCatalogResponse.Item> items = new ArrayList<>();
        List<LpuCatalogResponse.Skipped> skipped = new ArrayList<>();
        for (Lpu lpu : candidates) {
            switch (priceContractByLpu.get(lpu.id())) {
                case Resolution.Found found -> items.add(LpuCatalogResponse.Item.of(lpu));
                case Resolution.Ambiguous ambiguous -> skipped.add(skip(lpu, ambiguous));
                case Resolution.None none -> log.debug("каталог: у ЛПУ {} нет действующего договора", lpu.id());
            }
        }
        return new LpuCatalogResponse(items, skipped);
    }

    @Transactional(readOnly = true)
    public LpuCardResponse card(UUID lpuId) {
        Lpu lpu = activeLpu(lpuId);
        PriceContract priceContract = priceContracts.resolve(lpuId, calendar.today());
        return LpuCardResponse.of(lpu, priceContract.contract(), priceContract.kind());
    }

    @Transactional(readOnly = true)
    public LpuServicesResponse services(UUID lpuId, boolean needPrices) {
        Lpu lpu = activeLpu(lpuId);
        LocalDate today = calendar.today();
        PriceContract priceContract = priceContracts.resolve(lpuId, today);
        Map<UUID, AppealType> appealTypes = rvlAppealTypes.byExternalId();
        List<ContragentService> services = appealTypes.isEmpty()
                ? List.of()
                : contragentServices.findActiveByContractAndAppealTypeIn(
                        priceContract.contract().id(), appealTypes.keySet());
        Map<UUID, List<PriceListItem>> prices = needPrices ? currentPrices(services, today) : Map.of();
        List<LpuServicesResponse.Service> rows = services.stream()
                .map(service -> LpuServicesResponse.Service.of(
                        service,
                        appealTypes.get(service.externalAppealTypeId()),
                        needPrices ? prices.getOrDefault(service.id(), List.of()) : null))
                .toList();
        return LpuServicesResponse.of(lpu, priceContract.contract(), priceContract.kind(), rows);
    }

    /**
     * Базовая логика A, затем перечни: программы РВЛ действующих полисов договора
     * и учреждения из их перечней со строкой по виду обращения РВЛ (В-6). Сроки строки
     * перечня в отбор не входят (ТЗ на API, вопрос 5).
     */
    private List<Lpu> rvlLpuOf(UUID contractExternalId, LocalDate today) {
        List<UUID> programs = policies.findRvlProgramExternalIdsOfActivePolicies(contractExternalId, today);
        Set<UUID> appealTypes = rvlAppealTypes.externalIds();
        if (programs.isEmpty() || appealTypes.isEmpty()) {
            return List.of();
        }
        return lpus.findRvlCatalog(programs, appealTypes);
    }

    /**
     * Причина уходит вызывающему в {@code skipped} тем же кодом, каким ответила бы
     * точечная ручка, и в журнал: по таким записям собирается очередь на исправление
     * на стороне 1С (В-6).
     */
    private static LpuCatalogResponse.Skipped skip(Lpu lpu, Resolution.Ambiguous ambiguous) {
        log.warn("каталог: ЛПУ {} исключено, кандидатов на прайсовый договор больше одного: {}",
                lpu.id(), ambiguous.lpuContractIds());
        return new LpuCatalogResponse.Skipped(lpu.id(), AmbiguousReferenceException.AMBIGUOUS_PRICE_CONTRACT);
    }

    /** Цены всех услуг одним запросом, а не по услуге: в прейскуранте санатория десятки позиций. */
    private Map<UUID, List<PriceListItem>> currentPrices(List<ContragentService> services, LocalDate today) {
        if (services.isEmpty()) {
            return Map.of();
        }
        List<UUID> serviceIds = services.stream().map(ContragentService::id).toList();
        return priceListItems.findCurrentByServiceIdIn(serviceIds, today).stream()
                .collect(Collectors.groupingBy(item -> item.service().id()));
    }

    /** ЛПУ в архиве для каталога равносильно отсутствующему (ТЗ на API, R-02). */
    private Lpu activeLpu(UUID lpuId) {
        return lpus.findById(lpuId)
                .filter(lpu -> !lpu.isArchive())
                .orElseThrow(() -> ReferenceNotFoundException.lpu(lpuId));
    }
}

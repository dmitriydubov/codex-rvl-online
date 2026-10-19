package ru.projectx.datareference.mapper.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import ru.projectx.datareference.entity.AwaitedParent;
import ru.projectx.datareference.entity.catalog.ContragentService;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.entity.catalog.PriceListItem;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.CatalogReferences;
import ru.projectx.datareference.mapper.ForeignReferences;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.ForeignReferenceRepository;
import ru.projectx.datareference.repository.LpuContractRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.repository.PriceListItemRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PriceListMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID SERVICE = UUID.fromString("c329293a-0000-4000-8000-00000000000b");
    private static final UUID SERVICE_ROW_ID = UUID.fromString("c329293a-0000-4000-8000-0000000000bb");
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    private final PriceListItemRepository repository = mock(PriceListItemRepository.class);
    private final ContragentServiceRepository serviceRepository = mock(ContragentServiceRepository.class);
    private final ForeignReferenceRepository foreignRepository = mock(ForeignReferenceRepository.class);
    private final ContragentService service = mock(ContragentService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final PriceListMapper mapper = new PriceListMapper(repository, new CatalogReferences(
            mock(LpuRepository.class), mock(LpuContractRepository.class), serviceRepository,
            new ForeignReferences(foreignRepository, clock)), clock);

    @Test
    void transferForForeignService_isSkippedNamingTheService_andOldPricesStay() {
        when(serviceRepository.findByExternalId(SERVICE)).thenReturn(Optional.empty());
        when(foreignRepository.existsByExternalIdAndKind(SERVICE, ParentKind.CONTRAGENT_SERVICE)).thenReturn(true);

        ImportResult result = mapper.apply(transfer(row(1, "830")));

        assertThat(result).isEqualTo(ImportResult.skipped("прайс по услуге вне справочника: " + SERVICE));
        verifyNoInteractions(repository);
    }

    @Test
    void transferForServiceNotYetReceived_waitsForIt_andOldPricesStay() {
        when(serviceRepository.findByExternalId(SERVICE)).thenReturn(Optional.empty());

        ImportResult result = mapper.apply(transfer(row(1, "830")));

        assertThat(result).isEqualTo(
                ImportResult.awaitingParent(new AwaitedParent(ParentKind.CONTRAGENT_SERVICE, SERVICE)));
        verifyNoInteractions(repository);
    }

    @Test
    void transferForKnownService_replacesItsPricesWithTheTransferRows() {
        knownService();

        ImportResult result = mapper.apply(transfer(row(1, "830"), row(2, "1250.50")));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        InOrder order = inOrder(repository);
        order.verify(repository).deleteByServiceId(SERVICE_ROW_ID);
        order.verify(repository).saveAll(anyList());
        List<PriceListItem> saved = saved();
        assertThat(saved).extracting(PriceListItem::price)
                .containsExactly(new BigDecimal("830"), new BigDecimal("1250.50"));
        assertThat(saved).allSatisfy(item -> assertThat(item.service()).isSameAs(service));
    }

    @Test
    void row_carriesEveryFieldOfTheMessage() {
        knownService();

        mapper.apply(transfer(row(1, "830")));

        PriceListItem item = saved().getFirst();
        assertThat(item.serviceName()).isEqualTo("Лечебный массаж");
        assertThat(item.serviceCode()).isEqualTo("26.23.006/82");
        assertThat(item.serviceCodePf()).isEqualTo("26.23.006/82-ПФ");
        assertThat(item.priceWithDiscount()).isEqualByComparingTo("747.00");
        assertThat(item.validFrom()).isEqualTo(LocalDate.of(2026, 5, 13));
        assertThat(item.validTo()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void emptyOptionalFields_areStoredAsEmpty() {
        knownService();
        ObjectNode row = row(1, "830");
        row.remove("nameService");
        row.put("serviceCode", "   ");
        row.put("serviceCodeForPF", "");
        row.remove("priceWithDiscount");
        row.put("periodTo", "3000-01-01T00:00:00+03:00");

        mapper.apply(transfer(row));

        PriceListItem item = saved().getFirst();
        assertThat(item.serviceName()).isNull();
        assertThat(item.serviceCode()).isNull();
        assertThat(item.serviceCodePf()).isNull();
        assertThat(item.priceWithDiscount()).isNull();
        assertThat(item.validTo()).isNull();
    }

    @Test
    void emptyTransfer_clearsThePriceListOfTheService() {
        knownService();

        ImportResult result = mapper.apply(transfer());

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        verify(repository).deleteByServiceId(SERVICE_ROW_ID);
        assertThat(saved()).isEmpty();
    }

    @Test
    void rowWithoutPrice_isContractViolation_andOldPricesAreNotRemoved() {
        knownService();
        ObjectNode row = row(1, "830");
        row.remove("price");
        row.remove("period");

        assertThatThrownBy(() -> mapper.apply(transfer(row)))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("в строке прайса нет цены");
        verify(repository, never()).deleteByServiceId(SERVICE_ROW_ID);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void rowWithoutBeginDate_isContractViolation_andOldPricesAreNotRemoved() {
        knownService();
        ObjectNode row = row(1, "830");
        row.put("period", "0001-01-01T00:00:00+03:00");
        row.put("periodTo", "не дата");

        assertThatThrownBy(() -> mapper.apply(transfer(row)))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("в строке прайса нет даты начала действия");
        verify(repository, never()).deleteByServiceId(SERVICE_ROW_ID);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void checkedRow_followsTheSameRulesAsReplacement_withoutTouchingPrices() {
        ObjectNode withoutPrice = row(1, "830");
        withoutPrice.remove("price");
        ObjectNode withoutBeginDate = row(2, "830");
        withoutBeginDate.remove("period");

        mapper.checkRow(row(3, "830"));

        assertThatThrownBy(() -> mapper.checkRow(withoutPrice))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("в строке прайса нет цены");
        assertThatThrownBy(() -> mapper.checkRow(withoutBeginDate))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("в строке прайса нет даты начала действия");
        verifyNoInteractions(serviceRepository, repository);
    }

    @Test
    void transferWithoutServiceGuid_isContractViolation() {
        ObjectNode transfer = transfer(row(1, "830"));
        transfer.remove("keyValue");

        assertThatThrownBy(() -> mapper.apply(transfer))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageEndingWith(" keyValue");
        verifyNoInteractions(serviceRepository, repository);
    }

    private void knownService() {
        when(service.id()).thenReturn(SERVICE_ROW_ID);
        when(service.externalId()).thenReturn(SERVICE);
        when(serviceRepository.findByExternalId(SERVICE)).thenReturn(Optional.of(service));
    }

    private List<PriceListItem> saved() {
        ArgumentCaptor<List<PriceListItem>> saved = ArgumentCaptor.captor();
        verify(repository).saveAll(saved.capture());
        return saved.getValue();
    }

    private static ObjectNode transfer(ObjectNode... rows) {
        ObjectNode transfer = JSON.createObjectNode();
        transfer.put("keyValue", SERVICE.toString());
        List.of(rows).forEach(transfer.putArray("recordSet")::add);
        return transfer;
    }

    private static ObjectNode row(int rowNumber, String price) {
        ObjectNode row = JSON.createObjectNode();
        row.put("rowNumber", rowNumber);
        row.put("serviceId", SERVICE.toString());
        row.put("nameService", "Лечебный массаж ");
        row.put("serviceCode", "26.23.006/82");
        row.put("serviceCodeForPF", "26.23.006/82-ПФ");
        row.put("price", new BigDecimal(price));
        row.put("priceWithDiscount", new BigDecimal("747.00"));
        row.put("period", "2026-05-13T00:00:00+03:00");
        row.put("periodTo", "2026-12-31T00:00:00+03:00");
        return row;
    }
}

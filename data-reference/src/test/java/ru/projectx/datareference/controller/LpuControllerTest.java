package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.dto.LpuCardResponse;
import ru.projectx.datareference.dto.LpuCatalogResponse;
import ru.projectx.datareference.dto.LpuServicesResponse;
import ru.projectx.datareference.dto.PriceContractRef;
import ru.projectx.datareference.service.LpuCatalogService;

/** R-01…R-03: каталог ЛПУ договора, карточка ЛПУ и услуги с ценами и без. */
@ExtendWith(MockitoExtension.class)
class LpuControllerTest {

    private static final String LPU = InternalApi.BASE_PATH + "/lpu";
    private static final UUID LPU_ID = UUID.fromString("1b000000-0000-4000-8000-000000000001");
    private static final UUID LPU_EXTERNAL_ID = UUID.fromString("1b000000-0000-4000-8000-000000000002");
    private static final UUID CONTRACT_EXTERNAL_ID = UUID.fromString("1b000000-0000-4000-8000-000000000003");
    private static final UUID PRICE_CONTRACT_ID = UUID.fromString("1b000000-0000-4000-8000-000000000004");
    private static final String LPU_NAME = "Санаторий";

    @Mock
    private LpuCatalogService lpuCatalog;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.of(new LpuController(lpuCatalog));
    }

    @Test
    void catalog_returnsLpuOfContract_asJson() {
        when(lpuCatalog.catalog(CONTRACT_EXTERNAL_ID)).thenReturn(new LpuCatalogResponse(
                List.of(new LpuCatalogResponse.Item(LPU_ID, LPU_EXTERNAL_ID, LPU_NAME, null, null, null)), List.of()));

        MvcTestResult result = mvc.get().uri(LPU + "?contract_external_id={contract}", CONTRACT_EXTERNAL_ID).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(LPU_ID.toString());
    }

    @Test
    void catalog_withoutContract_isValidationErrorNamingParameter() {
        MvcTestResult result = mvc.get().uri(LPU).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("contract_external_id");
        verifyNoInteractions(lpuCatalog);
    }

    @Test
    void card_returnsLpuFoundByService() {
        when(lpuCatalog.card(LPU_ID)).thenReturn(
                new LpuCardResponse(LPU_ID, LPU_EXTERNAL_ID, LPU_NAME, null, null, null, null, priceContract()));

        MvcTestResult result = mvc.get().uri(LPU + "/{id}", LPU_ID).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(LPU_ID.toString());
    }

    @Test
    void services_withoutNeedPrices_askWithoutPrices() {
        when(lpuCatalog.services(LPU_ID, false)).thenReturn(services());

        MvcTestResult result = mvc.get().uri(LPU + "/{id}/services", LPU_ID).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.lpu.id").isEqualTo(LPU_ID.toString());
    }

    @Test
    void services_withNeedPrices_askWithPrices() {
        when(lpuCatalog.services(LPU_ID, true)).thenReturn(services());

        MvcTestResult result = mvc.get().uri(LPU + "/{id}/services?need_prices=true", LPU_ID).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.lpu.id").isEqualTo(LPU_ID.toString());
    }

    private static LpuServicesResponse services() {
        return new LpuServicesResponse(
                new LpuServicesResponse.LpuRef(LPU_ID, LPU_EXTERNAL_ID, LPU_NAME), priceContract(), List.of());
    }

    private static PriceContractRef priceContract() {
        return new PriceContractRef(PRICE_CONTRACT_ID, LPU_EXTERNAL_ID, null, LocalDate.of(2026, 1, 1), null, true,
                PriceContractRef.Kind.MAIN);
    }
}

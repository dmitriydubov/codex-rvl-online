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
import ru.projectx.datareference.dto.NamedRef;
import ru.projectx.datareference.dto.PriceContractRef;
import ru.projectx.datareference.service.lpu.LpuCardReads;
import ru.projectx.datareference.service.lpu.LpuCatalogReads;
import ru.projectx.datareference.service.lpu.LpuServiceReads;

@ExtendWith(MockitoExtension.class)
class LpuControllerTest {

    private static final String LPU = InternalApi.BASE_PATH + "/lpu";
    private static final UUID LPU_ID = UUID.fromString("1b000000-0000-4000-8000-000000000001");
    private static final UUID LPU_EXTERNAL_ID = UUID.fromString("1b000000-0000-4000-8000-000000000002");
    private static final UUID CONTRACT_EXTERNAL_ID = UUID.fromString("1b000000-0000-4000-8000-000000000003");
    private static final UUID PRICE_CONTRACT_ID = UUID.fromString("1b000000-0000-4000-8000-000000000004");
    private static final String LPU_NAME = "Санаторий";

    @Mock
    private LpuCatalogReads catalogs;

    @Mock
    private LpuCardReads cards;

    @Mock
    private LpuServiceReads services;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.of(new LpuController(catalogs, cards, services));
    }

    @Test
    void catalog_returnsLpuOfContract_asJson() {
        when(catalogs.catalog(CONTRACT_EXTERNAL_ID)).thenReturn(new LpuCatalogResponse(
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
        verifyNoInteractions(catalogs, cards, services);
    }

    @Test
    void card_returnsLpuFoundByService() {
        when(cards.card(LPU_ID)).thenReturn(
                new LpuCardResponse(LPU_ID, LPU_EXTERNAL_ID, LPU_NAME, null, null, null, null, priceContract()));

        MvcTestResult result = mvc.get().uri(LPU + "/{id}", LPU_ID).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(LPU_ID.toString());
    }

    @Test
    void services_withoutNeedPrices_askWithoutPrices() {
        when(services.withoutPrices(LPU_ID)).thenReturn(services());

        MvcTestResult result = mvc.get().uri(LPU + "/{id}/services", LPU_ID).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.lpu.id").isEqualTo(LPU_ID.toString());
    }

    @Test
    void services_withNeedPrices_askWithPrices() {
        when(services.withPrices(LPU_ID)).thenReturn(services());

        MvcTestResult result = mvc.get().uri(LPU + "/{id}/services?need_prices=true", LPU_ID).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.lpu.id").isEqualTo(LPU_ID.toString());
    }

    @Test
    void card_acceptingOnlyXml_isNotAcceptable_withoutAskingService() {
        MvcTestResult result = mvc.get().uri(LPU + "/{id}", LPU_ID).accept(MediaType.APPLICATION_XML).exchange();

        assertThat(result).hasStatus(406);
        verifyNoInteractions(catalogs, cards, services);
    }

    private static LpuServicesResponse services() {
        return new LpuServicesResponse(
                new NamedRef(LPU_ID, LPU_EXTERNAL_ID, LPU_NAME), priceContract(), List.of());
    }

    private static PriceContractRef priceContract() {
        return new PriceContractRef(PRICE_CONTRACT_ID, LPU_EXTERNAL_ID, null, LocalDate.of(2026, 1, 1), null, true,
                PriceContractRef.Kind.MAIN);
    }
}

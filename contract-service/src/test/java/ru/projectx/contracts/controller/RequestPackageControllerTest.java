package ru.projectx.contracts.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.dto.PackageItem;
import ru.projectx.contracts.dto.PackageItem.PackageAcceptance;
import ru.projectx.contracts.dto.PackageItem.PackageDocument;
import ru.projectx.contracts.dto.PackageItem.PackagePayment;
import ru.projectx.contracts.dto.PackageItem.PaymentState;
import ru.projectx.contracts.dto.PackageResponse;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.exception.RequestNotFoundException;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.service.packages.RequestPackageReads;
import ru.projectx.contracts.service.storage.AttachmentMetrics;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class RequestPackageControllerTest {

    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final UUID SPOUSE = UUID.fromString("9275471c-d2fe-484f-97a6-d6d7b1267b5e");
    private static final UUID CHILD = UUID.fromString("6778e8c2-985a-4d54-9bd3-0f6403b104d6");
    private static final UUID CONTRACT = UUID.fromString("3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4");
    private static final UUID FORM = UUID.fromString("c2e77a10-6b3d-4a02-8e19-5d4402fa77bb");
    private static final String PATH = "/internal/v1/requests/{request}/package";
    private static final String TRACE = "a41f8c02-7d3b-4e19-9a55-2c7e0b6f1d84";

    private final RequestPackageReads packages = mock(RequestPackageReads.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RequestPackageController(packages))
            .setControllerAdvice(new RequestFormatAdvice(new AttachmentMetrics(new SimpleMeterRegistry())),
                    new DomainErrorAdvice(), new UnexpectedErrorAdvice())
            .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()))
            .addFilters(new RequestIdFilter())
            .build();

    @Test
    void package_isAnsweredInSnakeCaseWithoutEmptyFieldsButWithDocuments() throws Exception {
        when(packages.packageOf(REQUEST)).thenReturn(new PackageResponse(REQUEST, "IN_PROGRESS", List.of(
                new PackageItem(SPOUSE, DocumentKind.KM_CONTRACT, CONTRACT, "DKM-2026-000001", 1,
                        DocumentState.ACCEPTED, new BigDecimal("34000.00"), null,
                        new PackageAcceptance(Instant.parse("2026-02-10T07:04:58Z"), 1),
                        new PackagePayment(PaymentState.PENDING, new BigDecimal("34000.00")), List.of(), null, null),
                new PackageItem(CHILD, DocumentKind.LM_POLICY, null, null, null, DocumentState.ISSUED, null,
                        "0555123456", null, null, List.of(new PackageDocument(FORM, "POLICY", "FINAL", 1,
                        "https://s3.example.test/lm/0555123456.pdf")), null, null))));

        mvc.perform(get(PATH, REQUEST).header(RequestIds.HEADER, TRACE))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIds.HEADER, TRACE))
                .andExpect(jsonPath("$.individual_request_id").value(REQUEST.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.items[0].participant_id").value(SPOUSE.toString()))
                .andExpect(jsonPath("$.items[0].document_kind").value("KM_CONTRACT"))
                .andExpect(jsonPath("$.items[0].contract_id").value(CONTRACT.toString()))
                .andExpect(jsonPath("$.items[0].contract_number").value("DKM-2026-000001"))
                .andExpect(jsonPath("$.items[0].contract_version").value(1))
                .andExpect(jsonPath("$.items[0].state").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].premium_amount").value(34000.00))
                .andExpect(jsonPath("$.items[0].acceptance.accepted_at").value("2026-02-10T07:04:58Z"))
                .andExpect(jsonPath("$.items[0].acceptance.contract_version").value(1))
                .andExpect(jsonPath("$.items[0].payment.status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].payment.amount").value(34000.00))
                .andExpect(jsonPath("$.items[0].documents.length()").value(0))
                .andExpect(jsonPath("$.items[0].policy_number").doesNotExist())
                .andExpect(jsonPath("$.items[0].error_code").doesNotExist())
                .andExpect(jsonPath("$.items[1].policy_number").value("0555123456"))
                .andExpect(jsonPath("$.items[1].contract_id").doesNotExist())
                .andExpect(jsonPath("$.items[1].payment").doesNotExist())
                .andExpect(jsonPath("$.items[1].documents[0].document_id").value(FORM.toString()))
                .andExpect(jsonPath("$.items[1].documents[0].doc_type").value("POLICY"))
                .andExpect(jsonPath("$.items[1].documents[0].kind").value("FINAL"))
                .andExpect(jsonPath("$.items[1].documents[0].version").value(1))
                .andExpect(jsonPath("$.items[1].documents[0].url").value("https://s3.example.test/lm/0555123456.pdf"));
    }

    @Test
    void unknownRequest_isNotFoundInTheCommonErrorFormat() throws Exception {
        when(packages.packageOf(REQUEST)).thenThrow(new RequestNotFoundException(REQUEST));

        mvc.perform(get(PATH, REQUEST).header(RequestIds.HEADER, TRACE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REQUEST_NOT_FOUND"))
                .andExpect(jsonPath("$.error.request_id").value(TRACE));
    }

    @Test
    void requestIdentifierThatIsNotAGuid_isValidationErrorOfThePathField() throws Exception {
        mvc.perform(get(PATH, "not-a-guid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("individual_request_id"));
        verifyNoInteractions(packages);
    }
}

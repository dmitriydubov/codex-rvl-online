package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

public class InsuranceContractCandidateSteps {

    private static final String CONTRACT_ID_BY_EXTERNAL_ID =
            "select cast(id as varchar) from insurance_contracts where external_id = :externalId";

    private final ContractsService contracts;
    private final JdbcClient jdbc;

    public InsuranceContractCandidateSteps(ContractsService contracts, JdbcClient jdbc) {
        this.contracts = contracts;
        this.jdbc = jdbc;
    }

    @Тогда("поле {string} элементов {string} ответа — id договоров страхования {string} и {string} в любом порядке")
    public void candidatesAreInsuranceContracts(String field, String list, String first, String second) {
        JsonNode elements = contracts.lastResponse().field(list);
        assertThat(elements.isArray()).as("поле %s — список: %s", list, elements).isTrue();

        List<String> candidates = elements.valueStream().map(element -> element.path(field).asString()).toList();

        assertThat(candidates).as("поле %s элементов %s", field, list)
                .containsExactlyInAnyOrder(contractId(first), contractId(second));
    }

    private String contractId(String externalId) {
        return jdbc.sql(CONTRACT_ID_BY_EXTERNAL_ID)
                .param("externalId", UUID.fromString(externalId))
                .query(String.class)
                .single();
    }
}

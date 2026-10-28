package ru.projectx.contracts.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractDraft;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;
import ru.projectx.contracts.entity.issuance.RequestReference;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.Tasks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ContractAcceptanceIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String TRACE = "a41f8c02-7d3b-4e19-9a55-2c7e0b6f1d84";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private ContractRepository contracts;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void removeCreatedContracts() {
        for (UUID contractId : created) {
            jdbc.sql("delete from contract_acceptances where contract_id = :contract").param("contract", contractId).update();
            contracts.deleteById(contractId);
        }
    }

    @Test
    void acceptance_isStoredWithTheAddressAsInetAndAcceptsTheContract() throws IOException, InterruptedException {
        UUID contractId = readyContract();

        HttpResponse<String> response = accept(contractId, "2001:db8::1");

        assertThat(response.statusCode()).as("тело %s", response.body()).isEqualTo(200);
        assertThat(json.readTree(response.body()).path("state").asString()).isEqualTo("ACCEPTED");
        assertThat(contracts.findById(contractId)).map(Contract::status).contains(ContractStatus.ACCEPTED);
        assertThat(jdbc.sql("select host(ip_address) from contract_acceptances where contract_id = :contract")
                .param("contract", contractId).query(String.class).single())
                .isEqualTo("2001:db8::1");
    }

    @Test
    void contractRowHeldByAnotherTransaction_isAnsweredServiceUnavailableWithoutAcceptance()
            throws IOException, InterruptedException, SQLException {
        UUID contractId = readyContract();

        HttpResponse<String> response;
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement("select id from contracts where id = ? for update")) {
                lock.setObject(1, contractId);
                lock.executeQuery();
                response = accept(contractId, "10.20.30.40");
            }
            finally {
                holder.rollback();
            }
        }

        assertThat(response.statusCode()).as("тело %s", response.body()).isEqualTo(503);
        JsonNode error = json.readTree(response.body()).path("error");
        assertThat(error.path("code").asString()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(error.path("request_id").asString()).isEqualTo(TRACE);
        assertThat(contracts.findById(contractId)).map(Contract::status).contains(ContractStatus.DRAFT_READY);
        assertThat(jdbc.sql("select count(*) from contract_acceptances where contract_id = :contract")
                .param("contract", contractId).query(Integer.class).single()).isZero();
    }

    private UUID readyContract() {
        IssuanceTerms terms = new IssuanceTerms(new RequestReference(UUID.randomUUID(), Tasks.INSURANCE_CONTRACT,
                Tasks.INSURER_POLICY), Tasks.INSURED, Tasks.PROGRAM, Issuances.PERIOD);
        Contract contract = Contract.open(new ContractDraft("DKM-IT-" + UUID.randomUUID().toString().substring(0, 8),
                terms, UUID.randomUUID(), new BigDecimal("34000.00")), Instant.parse("2026-02-10T07:05:00Z"));
        contract.offerDraft();
        UUID contractId = contracts.saveAndFlush(contract).id();
        created.add(contractId);
        return contractId;
    }

    private HttpResponse<String> accept(UUID contractId, String ipAddress) throws IOException, InterruptedException {
        String body = "{\"contract_version\": 1, \"accepted_by\": \"b0a1c7e4-52d9-4f31-8c06-7e3a9b1d2f45\","
                + " \"accepted_at\": \"2026-02-10T10:04:58+03:00\", \"channel\": \"WEB\", \"ip_address\": \""
                + ipAddress + "\"}";
        HttpRequest call = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/contracts/"
                        + contractId + "/acceptance"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header(RequestIds.HEADER, TRACE)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return http.send(call, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}

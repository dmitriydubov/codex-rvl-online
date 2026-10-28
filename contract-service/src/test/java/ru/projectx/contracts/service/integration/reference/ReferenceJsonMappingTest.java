package ru.projectx.contracts.service.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.projectx.contracts.config.ReferenceProperties;
import ru.projectx.contracts.dto.ReferenceProgram;
import ru.projectx.contracts.support.StandExample;
import tools.jackson.databind.json.JsonMapper;

class ReferenceJsonMappingTest {

    private static final String BASE = "http://reference.example/internal/v1";
    private static final UUID PROGRAM_ID = UUID.fromString("7d1f1a26-3a0f-4a4e-8d7f-5a2b3c4d5e6f");
    private static final UUID LPU_ID = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withPropertyValues("spring.jackson.property-naming-strategy=SNAKE_CASE");

    @Test
    void bootBuiltMapper_readsTheReferenceContract() {
        contexts.run(context -> {
            ReferenceProgram program = readProgram(context.getBean(JsonMapper.class));

            assertThat(program.id()).isEqualTo(PROGRAM_ID);
            assertThat(program.externalId()).isEqualTo(UUID.fromString("995448e7-dd5b-499a-8a4e-97b363d06dd3"));
            assertThat(program.typeProgramm()).isEqualTo("СКЛ");
            assertThat(program.services()).singleElement()
                    .satisfies(service -> assertThat(service.lpuExternalId())
                            .isEqualTo(UUID.fromString("b515b8fc-d13c-4b0b-aae3-33e0ca86ff1c")));
        });
    }

    private static ReferenceProgram readProgram(JsonMapper bootMapper) {
        ReferenceProperties settings = new ReferenceProperties(BASE, Duration.ofSeconds(2), Duration.ofSeconds(7),
                new ReferenceProperties.Retry(0, Duration.ZERO));
        RestClient.Builder builder = ReferenceRestClients.builder(settings, bootMapper);
        MockRestServiceServer reference = MockRestServiceServer.bindTo(builder).build();
        reference.expect(once(), requestTo(BASE + "/programs/" + PROGRAM_ID))
                .andRespond(withSuccess(StandExample.load("05-program.json")
                        .body(Map.of("program_id", PROGRAM_ID.toString(), "lpu_id", LPU_ID.toString())),
                        MediaType.APPLICATION_JSON));
        ReferenceHttpClient client =
                new ReferenceHttpClient(builder.build(), ReferenceRetries.of(settings.retry()));

        ReferenceProgram program = client.program(PROGRAM_ID);
        reference.verify();
        return program;
    }
}

package ru.projectx.datareference.controller.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.ResolvableType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import ru.projectx.datareference.controller.InternalApi;
import ru.projectx.datareference.controller.StandaloneMvc;
import ru.projectx.datareference.dto.ErrorResponse;
import tools.jackson.databind.json.JsonMapper;

class OpenApiContractTest {

    private static final Set<String> PROBES = Set.of("/livez", "/readyz");
    private static final String PROBES_PROPERTY = "management.endpoint.health.probes.";

    private static OpenApiDescription description;

    private final WebApplicationContextRunner api = ApiContext.runner();
    private final Mismatches mismatches = new Mismatches();

    @BeforeAll
    static void readDescription() throws IOException {
        description = OpenApiDescription.read(OpenApiDescription.FILE);
    }

    @Test
    void everyHandlerIsDescribedWithItsPathQueryParametersAndTheirRequiredness() {
        api.run(context -> new EndpointComparison(description, mismatches)
                .compare(CodeEndpoints.of(context.getBean(RequestMappingHandlerMapping.class)), InternalApi.BASE_PATH));

        mismatches.assertNone();
    }

    @Test
    void responseFieldsMatchTheDescribedSchemasInNamesRequirednessAndNullability() {
        api.run(context -> {
            SchemaComparison schemas = new SchemaComparison(description,
                    new JsonProperties(context.getBean(JsonMapper.class)), mismatches);
            Map<String, YamlNode> operations = description.operations();
            CodeEndpoints.of(context.getBean(RequestMappingHandlerMapping.class)).entrySet().stream()
                    .filter(endpoint -> operations.containsKey(endpoint.getKey()))
                    .forEach(endpoint -> compareResponses(schemas, endpoint.getKey(), endpoint.getValue()));
            schemas.compare(ResolvableType.forClass(ErrorResponse.class),
                    description.component("schemas", "Error"), "схема Error");
        });

        mismatches.assertNone();
    }

    @Test
    void errorTableListsExactlyTheStatusesAndCodesTheAdvicesAnswer() {
        mismatches.compareNames("таблица ошибок в info.description", "статус и код", answeredErrors(),
                ApiErrorCodes.described(description));

        mismatches.assertNone();
    }

    @Test
    void errorCatalogOfThisTestReachesEveryDeclaredCodeAndEveryExceptionHandler() {
        Set<String> answeredCodes = answeredErrors().stream()
                .map(pair -> pair.substring(pair.indexOf(' ') + 1))
                .collect(Collectors.toCollection(TreeSet::new));
        List<Exception> failures = ErrorCatalog.failures();

        assertThat(answeredCodes).as("коды из констант пакетов exception и controller.error: добавьте случай в ErrorCatalog")
                .containsAll(ApiErrorCodes.declared());
        assertThat(ApiErrorCodes.handledFailures()).as("@ExceptionHandler без случая в ErrorCatalog")
                .allMatch(handled -> failures.stream().anyMatch(handled::isInstance));
        assertThat(AdviceHandlers.unreachedBy(failures))
                .as("@ExceptionHandler, который не отвечает ни на один случай ErrorCatalog: добавьте случай")
                .isEmpty();
    }

    @Test
    void probesAreTheOnlyDescribedPathsOutsideTheApiAndTheServicePublishesThem() {
        Set<String> outsideApi = description.paths().stream()
                .filter(path -> !path.startsWith(InternalApi.BASE_PATH + "/"))
                .collect(Collectors.toSet());

        assertThat(outsideApi).as("пути вне %s в описании", InternalApi.BASE_PATH).isEqualTo(PROBES);
        api.run(context -> assertThat(List.of(
                context.getEnvironment().getProperty(PROBES_PROPERTY + "enabled", Boolean.class),
                context.getEnvironment().getProperty(PROBES_PROPERTY + "add-additional-paths", Boolean.class)))
                .as("пробы /livez и /readyz на порту API").containsOnly(true));
    }

    private void compareResponses(SchemaComparison schemas, String operation, List<HandlerMethod> handlers) {
        YamlNode schema = description.okSchema(description.operations().get(operation));
        handlers.stream()
                .map(handler -> ResolvableType.forMethodReturnType(handler.getMethod()))
                .collect(Collectors.toMap(ResolvableType::toString, type -> type, (first, second) -> first))
                .values()
                .forEach(type -> schemas.compare(type, schema, operation + " → 200"));
    }

    private static Set<String> answeredErrors() {
        List<Exception> failures = ErrorCatalog.failures();
        MockMvcTester mvc = StandaloneMvc.withRequestId(new FailureController(failures), StandaloneMvc.errorAdvices());
        return IntStream.range(0, failures.size())
                .mapToObj(index -> answer(mvc.get().uri(FailureController.PATH, index).exchange().getResponse()))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String answer(MockHttpServletResponse response) {
        String code = new JsonMapper().readTree(response.getContentAsByteArray()).at("/error/code").asString();
        return ApiErrorCodes.pair(response.getStatus(), code);
    }
}

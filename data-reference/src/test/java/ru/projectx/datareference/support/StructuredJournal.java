package ru.projectx.datareference.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class StructuredJournal implements AutoCloseable {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final ConfigurableApplicationContext application;

    private StructuredJournal(ConfigurableApplicationContext application) {
        this.application = application;
    }

    public static StructuredJournal start() {
        return new StructuredJournal(new SpringApplicationBuilder(NoBeans.class)
                .web(WebApplicationType.NONE)
                .run());
    }

    @Override
    public void close() {
        application.close();
    }

    public static List<JsonNode> records(CapturedOutput output) {
        List<JsonNode> records = output.getOut().lines()
                .filter(line -> !line.isBlank())
                .map(JSON::readTree)
                .toList();
        assertThat(records).isNotEmpty().allSatisfy(record -> assertThat(record.isObject()).isTrue());
        return records;
    }

    public static JsonNode recordWith(CapturedOutput output, String fragment) {
        List<JsonNode> matching = records(output).stream()
                .filter(record -> record.path("message").asString().contains(fragment))
                .toList();
        assertThat(matching).as("записи журнала с текстом «%s»", fragment).hasSize(1);
        return matching.getFirst();
    }

    @Configuration(proxyBeanMethods = false)
    static class NoBeans {
    }
}

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

/**
 * Журнал сервиса в том формате, в каком его пишет приложение, и чтение его записей в тестах.
 *
 * <p>Формат журнала задаёт {@code application.yml}, а применяет его Spring Boot при запуске приложения. Поэтому
 * {@link #start()} запускает приложение с теми же настройками, но без единого бина: база, брокер и веб-сервер
 * не нужны, нужна только настройка журнала. После этого всё, что сервис пишет в журнал, выходит в консоль
 * в рабочем формате, и тест читает его через перехват вывода Spring Boot.
 */
public final class StructuredJournal implements AutoCloseable {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final ConfigurableApplicationContext application;

    private StructuredJournal(ConfigurableApplicationContext application) {
        this.application = application;
    }

    /** Запускает приложение без бинов, чтобы журнал настроился так же, как в работающем сервисе. */
    public static StructuredJournal start() {
        return new StructuredJournal(new SpringApplicationBuilder(NoBeans.class)
                .web(WebApplicationType.NONE)
                .run());
    }

    @Override
    public void close() {
        application.close();
    }

    /**
     * Все записи стандартного вывода. Каждая непустая строка обязана быть отдельным объектом JSON: иначе
     * сборщик журналов контура не разберёт её.
     */
    public static List<JsonNode> records(CapturedOutput output) {
        List<JsonNode> records = output.getOut().lines()
                .filter(line -> !line.isBlank())
                .map(JSON::readTree)
                .toList();
        assertThat(records).isNotEmpty().allSatisfy(record -> assertThat(record.isObject()).isTrue());
        return records;
    }

    /** Единственная запись, текст которой содержит {@code fragment}. */
    public static JsonNode recordWith(CapturedOutput output, String fragment) {
        List<JsonNode> matching = records(output).stream()
                .filter(record -> record.path("message").asString().contains(fragment))
                .toList();
        assertThat(matching).as("записи журнала с текстом «%s»", fragment).hasSize(1);
        return matching.getFirst();
    }

    /** Пустая конфигурация: тесту нужно только настроенное приложением журналирование. */
    @Configuration(proxyBeanMethods = false)
    static class NoBeans {
    }
}

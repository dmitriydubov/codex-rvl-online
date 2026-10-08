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
 * Журнал сервиса в рабочем формате из {@code application.yml}: {@link #start()} запускает приложение без бинов только
 * ради настройки журнала. Настройка остаётся в процессе и после {@link #close()}, поэтому проверки журнала в других
 * тестах не должны зависеть от формата строки: они ищут текст записи и ключи MDC.
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

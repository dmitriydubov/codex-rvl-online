package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Проверки ответа на уровне HTTP: заголовок {@code Allow}, тип содержимого, пустое тело и отсутствие
 * заголовка {@code X-Request-Id} у ответа, который отдал сам сервер, а не сервис.
 *
 * <p>{@code Allow} сравнивается как перечень методов без учёта порядка и пробелов после запятой. Лишний,
 * недостающий или повторённый метод проверку проваливает. Тип содержимого сравнивается по типу и
 * подтипу, параметры вроде {@code charset} не значимы.
 */
public class ProtocolResponseSteps {

    /** Перечень методов в шаге: имена через запятую, пробелы вокруг запятой не значимы. */
    private static final Pattern METHOD_LIST = Pattern.compile("\\s*,\\s*");

    private final ContractsService contracts;

    public ProtocolResponseSteps(ContractsService contracts) {
        this.contracts = contracts;
    }

    @Тогда("заголовок Allow ответа перечисляет методы {string}")
    public void allowLists(String methods) {
        assertThat(contracts.lastResponse().headers().getValuesAsList(HttpHeaders.ALLOW))
                .as("заголовок Allow")
                .containsExactlyInAnyOrder(METHOD_LIST.split(methods.strip()));
    }

    @Тогда("тип содержимого ответа — {string}")
    public void contentTypeIs(String expected) {
        MediaType actual = contracts.lastResponse().headers().getContentType();
        assertThat(actual).as("заголовок Content-Type").isNotNull();
        assertThat(actual.getType() + "/" + actual.getSubtype()).as("тип содержимого").isEqualTo(expected);
    }

    @Тогда("тело ответа пустое")
    public void bodyIsEmpty() {
        assertThat(contracts.lastResponse().bodyBytes()).as("размер тела ответа в байтах").isZero();
    }

    @Тогда("заголовка X-Request-Id в ответе нет")
    public void noRequestId() {
        assertThat(contracts.lastResponse().requestIds()).as("заголовки X-Request-Id ответа").isEmpty();
    }
}

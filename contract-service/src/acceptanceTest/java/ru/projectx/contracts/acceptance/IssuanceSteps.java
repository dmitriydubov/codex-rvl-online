package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.assertj.core.api.SoftAssertions;
import org.jspecify.annotations.Nullable;

public class IssuanceSteps {

    private static final Set<String> KNOWN = Set.of("документ", "статус", "номер", "начало", "окончание", "премия",
            "страхователь", "договор страхования", "полис страхователя", "версия", "причина прекращения", "комментарий",
            "момент прекращения", "код ошибки", "текст ошибки");

    private final IssuanceRecords records;

    public IssuanceSteps(IssuanceRecords records) {
        this.records = records;
    }

    @Тогда("выпуск по участнику {string} заявки {string}:")
    public void latestIssuance(String participant, String request, Map<String, String> expected) {
        List<IssuanceRecords.Record> all = records.of(participant, request);
        check(expected, all.isEmpty() ? Optional.empty() : Optional.of(all.get(all.size() - 1)));
    }

    @Тогда("предыдущий выпуск по участнику {string} заявки {string}:")
    public void previousIssuance(String participant, String request, Map<String, String> expected) {
        List<IssuanceRecords.Record> all = records.of(participant, request);
        assertThat(all).as("записей о выпуске по участнику %s", participant).hasSizeGreaterThan(1);
        check(expected, Optional.of(all.get(all.size() - 2)));
    }

    @Тогда("записи о выпуске участника {string} заявки {string} в порядке создания — {string}")
    public void recordsInOrder(String participant, String request, String summaries) {
        assertThat(records.of(participant, request)).extracting(IssuanceRecords.Record::summary)
                .containsExactlyElementsOf(Arrays.stream(summaries.split(",")).map(String::trim).toList());
    }

    @Тогда("стороны выпуска участника {string} заявки {string}:")
    public void parties(String participant, String request, List<Map<String, String>> expected) {
        List<IssuanceRecords.Record> all = records.of(participant, request);
        assertThat(all).as("записи о выпуске по участнику %s", participant).isNotEmpty();
        List<IssuanceRecords.Person> persons = records.personsOf(all.get(all.size() - 1).id());
        assertThat(persons).hasSameSizeAs(expected);
        SoftAssertions softly = new SoftAssertions();
        for (int index = 0; index < expected.size(); index++) {
            party(softly, expected.get(index), persons.get(index));
        }
        softly.assertAll();
    }

    private static void party(SoftAssertions softly, Map<String, String> expected, IssuanceRecords.Person person) {
        softly.assertThat(person.role()).isEqualTo(expected.get("роль"));
        softly.assertThat(person.participant()).isEqualTo(UUID.fromString(expected.get("участник")));
        softly.assertThat(person.lastName()).isEqualTo(expected.get("фамилия"));
        softly.assertThat(person.firstName()).isEqualTo(expected.get("имя"));
        softly.assertThat(person.middleName()).isEqualTo(expected.get("отчество"));
        softly.assertThat(person.birthDate().toString()).isEqualTo(expected.get("дата рождения"));
        softly.assertThat(person.gender()).isEqualTo(expected.get("пол"));
        softly.assertThat(person.relationType()).isEqualTo(blankAsNull(expected.get("родство")));
        softly.assertThat(person.documentType()).isEqualTo(expected.get("документ"));
        softly.assertThat(person.documentSeries()).isEqualTo(expected.get("серия"));
        softly.assertThat(person.documentNumber()).isEqualTo(expected.get("номер документа"));
    }

    private static void check(Map<String, String> expected, Optional<IssuanceRecords.Record> record) {
        StepTables.onlyKnown(expected, KNOWN);
        String document = StepTables.required(expected, "документ");
        if ("ничего".equals(document)) {
            assertThat(record).as("запись о выпуске").isEmpty();
            return;
        }
        assertThat(record).as("запись о выпуске").isPresent();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(record.get().document()).isEqualTo(document);
        requisites(softly, expected, record.get());
        softly.assertAll();
    }

    private static void requisites(SoftAssertions softly, Map<String, String> expected, IssuanceRecords.Record record) {
        requisite(softly, expected, "статус", record::status);
        requisite(softly, expected, "номер", record::number);
        requisite(softly, expected, "начало", () -> text(record.dateFrom()));
        requisite(softly, expected, "окончание", () -> text(record.dateTo()));
        requisite(softly, expected, "премия", () -> amount(record.premium()));
        requisite(softly, expected, "страхователь", () -> text(record.policyholder()));
        requisite(softly, expected, "договор страхования", () -> text(record.insuranceContract()));
        requisite(softly, expected, "полис страхователя", () -> text(record.insurerPolicy()));
        requisite(softly, expected, "версия", () -> Integer.toString(record.version()));
        requisite(softly, expected, "причина прекращения", record::cancelReason);
        requisite(softly, expected, "комментарий", record::cancelComment);
        requisite(softly, expected, "код ошибки", record::errorCode);
        requisite(softly, expected, "текст ошибки", record::errorMessage);
        if (expected.containsKey("момент прекращения")) {
            softly.assertThat(record.cancelledAt()).as("момент прекращения")
                    .isEqualTo(moment(blankAsNull(expected.get("момент прекращения"))));
        }
    }

    private static void requisite(SoftAssertions softly, Map<String, String> expected, String key,
            Supplier<@Nullable String> actual) {
        if (expected.containsKey(key)) {
            softly.assertThat(actual.get()).as(key).isEqualTo(blankAsNull(expected.get(key)));
        }
    }

    private static @Nullable String amount(@Nullable BigDecimal value) {
        return Optional.ofNullable(value).map(amount -> amount.setScale(2).toPlainString()).orElse(null);
    }

    private static @Nullable String text(@Nullable Object value) {
        return Optional.ofNullable(value).map(Object::toString).orElse(null);
    }

    private static @Nullable Instant moment(@Nullable String value) {
        return Optional.ofNullable(value).map(OffsetDateTime::parse).map(OffsetDateTime::toInstant).orElse(null);
    }

    private static @Nullable String blankAsNull(@Nullable String value) {
        return Optional.ofNullable(value).filter(text -> !text.isBlank()).orElse(null);
    }
}

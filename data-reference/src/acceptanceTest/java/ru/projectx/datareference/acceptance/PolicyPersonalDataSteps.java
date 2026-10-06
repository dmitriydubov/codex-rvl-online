package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Тогда;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Шаги обезличивания полисов: часы около границы запаса, состояние персональных данных полиса прямо в базе и журнал
 * прохода стирания. Запас берётся из настроек сервиса, поэтому сценарии закрепляют границу, а не число суток.
 * Состояние читается из таблицы полисов, а не из ответа: иначе маскировку при чтении не отличить от стирания.
 */
public class PolicyPersonalDataSteps {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Map<String, Duration> AROUND_GRACE = Map.of("больше", HOUR, "меньше", HOUR.negated());
    private static final String OPEN_END = "3000-01-01";
    private static final int DAY_LENGTH = OPEN_END.length();
    private static final String STORED = """
            select number, cast(begin_date as text) as begin_date, cast(end_date as text) as end_date, is_archive,
                   gender, last_name, first_name, middle_name, cast(date_of_birth as text) as date_of_birth, email,
                   personal_data_erased_at is not null as erased
              from policies where external_id = :externalId
            """;

    private final StandClock clock;
    private final ImportProperties properties;
    private final OneCInbox inbox;
    private final ServiceJournal journal;
    private final JdbcClient jdbc;

    public PolicyPersonalDataSteps(
            StandClock clock, ImportProperties properties, OneCInbox inbox, ServiceJournal journal, JdbcClient jdbc) {

        this.clock = clock;
        this.properties = properties;
        this.inbox = inbox;
        this.journal = journal;
        this.jdbc = jdbc;
    }

    @Дано("часы контура остановлены на час {word} запаса обезличивания после {string} по Москве")
    public void clockAroundGrace(String moreOrLess, String moment) {
        assertThat(AROUND_GRACE).as("сторона границы, которую умеет изображать шаг").containsKey(moreOrLess);
        clock.stopAt(moscow(moment).plus(grace()).plus(AROUND_GRACE.get(moreOrLess)).toInstant());
    }

    @Дано("часы контура остановлены на середине запаса обезличивания после {string} по Москве")
    public void clockHalfwayThroughGrace(String moment) {
        clock.stopAt(moscow(moment).plus(grace().dividedBy(2)).toInstant());
    }

    @Тогда("персональные данные полиса {string} в базе Дата-референса {word}")
    public void personalDataState(String externalId, String state) {
        StoredPolicy stored = stored(externalId);
        StoredPolicy sent = StoredPolicy.of(lastPolicy(externalId));
        switch (state) {
            case "обезличены" -> assertThat(stored).as("полис после обезличивания").isEqualTo(sent.withPersonalDataErased());
            case "сохранены" -> assertThat(stored).as("полис с персональными данными из сообщения").isEqualTo(sent);
            default -> throw new IllegalArgumentException("шаг не знает состояния персональных данных: " + state);
        }
    }

    @Тогда("ФИО, дата рождения и e-mail застрахованных из присланных полисов не попали в журнал Дата-референса")
    public void personalDataNotLogged() {
        inbox.receivedOf(OneCStream.POLICIES).stream()
                .flatMap(message -> StoredPolicy.of(message.data()).personalValues())
                .distinct()
                .forEach(value -> assertThat(journal.mentioning(value))
                        .as("записи журнала с персональными данными застрахованного").isEmpty());
    }

    @Тогда("в журнале Дата-референса есть запись уровня INFO об обезличивании полисов: обезличено {int}")
    public void anonymizationLogged(int erased) {
        String expected = "обезличены полисы: %d, после окончания срока или перевода в архив прошло больше %s"
                .formatted(erased, grace());

        assertThat(journal.events())
                .as("запись INFO об обезличивании полисов: %s", expected)
                .filteredOn(event -> event.getLevel() == Level.INFO && expected.equals(event.getFormattedMessage()))
                .hasSize(1);
    }

    private Duration grace() {
        return properties.policyPersonalDataGrace();
    }

    private StoredPolicy stored(String externalId) {
        return jdbc.sql(STORED)
                .param("externalId", UUID.fromString(externalId))
                .query((row, number) -> new StoredPolicy(row.getString("number"), row.getString("begin_date"),
                        row.getString("end_date"), row.getBoolean("is_archive"), row.getString("gender"),
                        new PersonalData(row.getString("last_name"), row.getString("first_name"),
                                row.getString("middle_name"), row.getString("date_of_birth"), row.getString("email")),
                        row.getBoolean("erased")))
                .single();
    }

    private ObjectNode lastPolicy(String externalId) {
        return inbox.receivedOf(OneCStream.POLICIES).reversed().stream()
                .map(OneCMessage::data)
                .filter(data -> externalId.equals(data.path("id").asString()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("сценарий не присылал полис " + externalId));
    }

    private static ZonedDateTime moscow(String moment) {
        return LocalDateTime.parse(moment, MOMENT).atZone(MOSCOW);
    }

    /** ФИО, дата рождения ({@code yyyy-mm-dd}) и почта застрахованного. */
    private record PersonalData(@Nullable String lastName, @Nullable String firstName, @Nullable String middleName,
                                @Nullable String dateOfBirth, @Nullable String email) {

        static final PersonalData NONE = new PersonalData(null, null, null, null, null);

        Stream<String> values() {
            return Stream.of(lastName, firstName, middleName, dateOfBirth, email).filter(Objects::nonNull);
        }
    }

    /** Полис, как его видно в таблице, или каким его должно сохранить сообщение 1С. */
    private record StoredPolicy(String number, String beginDate, @Nullable String endDate, boolean archive,
                                @Nullable String gender, PersonalData personalData, boolean erased) {

        static StoredPolicy of(ObjectNode data) {
            JsonNode header = data.path("header").path(0);
            return new StoredPolicy(data.path("number").asString(), day(header.path("beginDate")),
                    Optional.ofNullable(day(header.path("endDate"))).filter(end -> !OPEN_END.equals(end)).orElse(null),
                    data.path("deleteMark").asBoolean(), text(data.path("gender")),
                    new PersonalData(text(data.path("lastName")), text(data.path("firstName")),
                            text(data.path("middleName")), day(data.path("dateOfBirth")),
                            text(data.path("extProperties").path(0).path("value").path("value"))),
                    false);
        }

        StoredPolicy withPersonalDataErased() {
            return new StoredPolicy(number, beginDate, endDate, archive, gender, PersonalData.NONE, true);
        }

        Stream<String> personalValues() {
            return personalData.values();
        }

        private static @Nullable String text(JsonNode node) {
            return node.isMissingNode() ? null : node.asString();
        }

        /** Дата сообщения 1С бывает моментом; в таблице — календарный день, как он написан. */
        private static @Nullable String day(JsonNode node) {
            return node.isMissingNode() ? null : node.asString().substring(0, DAY_LENGTH);
        }
    }
}

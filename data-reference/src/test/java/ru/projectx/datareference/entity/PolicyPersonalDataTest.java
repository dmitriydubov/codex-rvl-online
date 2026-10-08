package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * После границы хранения полис теряет ФИО, дату рождения и e-mail, а реквизиты, пол и признак архива остаются:
 * на строку полиса ссылается сервис Договоров. Момент перевода в архив сдвигает только настоящий перевод,
 * а не повторная присылка того же сообщения.
 */
class PolicyPersonalDataTest {

    private static final Instant RECEIVED = Instant.parse("2026-03-01T09:00:00Z");
    private static final Instant ARCHIVED = Instant.parse("2026-03-10T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-04-20T09:00:00Z");
    private static final LocalDate END = LocalDate.of(2026, 1, 31);
    private static final LocalDate FAR_END = LocalDate.of(2030, 12, 31);

    @Test
    void endedBeforeCutoffDay_erasesNameBirthDateAndEmail_keepsTheRest() {
        Policy policy = policy(END, false, RECEIVED);

        policy.erasePersonalDataBeyond(new PersonalDataCutoff(END.plusDays(1), RECEIVED.minusSeconds(1)), LATER);

        Policy.Details details = policy.details();
        assertThat(details.lastName()).isNull();
        assertThat(details.firstName()).isNull();
        assertThat(details.middleName()).isNull();
        assertThat(details.dateOfBirth()).isNull();
        assertThat(details.email()).isNull();
        assertThat(details.number()).isEqualTo("П-0001");
        assertThat(details.gender()).isEqualTo("Мужской");
        assertThat(details.endDate()).isEqualTo(END);
        assertThat(policy.personalDataErased()).isTrue();
        assertThat(policy.updatedAt()).isEqualTo(RECEIVED);
    }

    @Test
    void endingOnCutoffDay_keepsPersonalData() {
        Policy policy = policy(END, false, RECEIVED);

        policy.erasePersonalDataBeyond(new PersonalDataCutoff(END, RECEIVED.minusSeconds(1)), LATER);

        assertThat(policy.details().lastName()).isEqualTo("Тестов");
        assertThat(policy.personalDataErased()).isFalse();
    }

    @Test
    void openEndedPolicyNotInArchive_keepsPersonalData_howeverLate() {
        Policy policy = policy(null, false, RECEIVED);

        policy.erasePersonalDataBeyond(new PersonalDataCutoff(LocalDate.MAX, LATER), LATER);

        assertThat(policy.personalDataErased()).isFalse();
    }

    @Test
    void archivedNoLaterThanCutoff_erasesPersonalData() {
        Policy policy = policy(FAR_END, true, ARCHIVED);

        policy.erasePersonalDataBeyond(new PersonalDataCutoff(END, ARCHIVED), LATER);

        assertThat(policy.personalDataErased()).isTrue();
        assertThat(policy.details().lastName()).isNull();
    }

    @Test
    void archivedAfterCutoff_keepsPersonalData() {
        Policy policy = policy(FAR_END, true, ARCHIVED);

        policy.erasePersonalDataBeyond(new PersonalDataCutoff(END, ARCHIVED.minusSeconds(1)), LATER);

        assertThat(policy.personalDataErased()).isFalse();
    }

    @Test
    void resentArchivedPolicy_keepsTheMomentItWasArchived() {
        Policy policy = policy(FAR_END, true, ARCHIVED);

        policy.applySnapshot(snapshot(FAR_END, true), LATER);
        policy.erasePersonalDataBeyond(new PersonalDataCutoff(END, ARCHIVED), LATER);

        assertThat(policy.personalDataErased()).isTrue();
    }

    @Test
    void policyArchivedLater_countsFromTheMomentOfArchiving() {
        Policy policy = policy(FAR_END, false, RECEIVED);

        policy.applySnapshot(snapshot(FAR_END, true), ARCHIVED);
        policy.erasePersonalDataBeyond(new PersonalDataCutoff(END, ARCHIVED.minusSeconds(1)), LATER);

        assertThat(policy.personalDataErased()).isFalse();
    }

    @Test
    void policyTakenOutOfArchive_isNoLongerErasedByArchiveMoment() {
        Policy policy = policy(FAR_END, true, ARCHIVED);

        policy.applySnapshot(snapshot(FAR_END, false), LATER);
        policy.erasePersonalDataBeyond(new PersonalDataCutoff(END, LATER), LATER);

        assertThat(policy.personalDataErased()).isFalse();
    }

    @Test
    void newMessage_bringsPersonalDataBack_andClearsTheMark() {
        Policy policy = policy(END, false, RECEIVED);
        policy.erasePersonalDataBeyond(new PersonalDataCutoff(END.plusDays(1), RECEIVED.minusSeconds(1)), LATER);

        policy.applySnapshot(snapshot(FAR_END, false), LATER);

        assertThat(policy.details().lastName()).isEqualTo("Тестов");
        assertThat(policy.details().dateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(policy.personalDataErased()).isFalse();
    }

    private static Policy policy(@Nullable LocalDate endDate, boolean deleteMark, Instant now) {
        return Policy.create(UUID.randomUUID(), snapshot(endDate, deleteMark), now);
    }

    private static Policy.Snapshot snapshot(@Nullable LocalDate endDate, boolean deleteMark) {
        var details = new Policy.Details(UUID.fromString("77ff17b5-e659-4720-b57f-e8a19c00fcea"), "П-0001", "Тестов",
                "Тест", "Тестович", LocalDate.of(1990, 1, 1), "Мужской", "test@example.org", LocalDate.of(2026, 1, 1),
                endDate, true, deleteMark);
        return new Policy.Snapshot(details, List.of(), List.of());
    }
}

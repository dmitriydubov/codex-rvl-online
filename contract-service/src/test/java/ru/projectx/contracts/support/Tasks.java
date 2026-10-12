package ru.projectx.contracts.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.message.ParticipantDocument;
import ru.projectx.contracts.message.ParticipantPerson;
import ru.projectx.contracts.message.TaskParticipant;

public final class Tasks {

    public static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    public static final UUID INSURANCE_CONTRACT = UUID.fromString("e12aab1a-1582-4b3a-af36-d30e637953f1");
    public static final UUID INSURER_POLICY = UUID.fromString("a23ba061-f742-4b20-8b95-36a0e525c087");
    public static final UUID INSURED = UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc");
    public static final UUID SPOUSE = UUID.fromString("9275471c-d2fe-484f-97a6-d6d7b1267b5e");
    public static final UUID CHILD = UUID.fromString("6778e8c2-985a-4d54-9bd3-0f6403b104d6");
    public static final LocalDate FROM = LocalDate.parse("2026-04-10");
    public static final LocalDate TO = LocalDate.parse("2026-04-24");

    private Tasks() {
    }

    public static ContractTask task(TaskParticipant... participants) {
        return new ContractTask(REQUEST, INSURANCE_CONTRACT, INSURER_POLICY, FROM, TO, List.of(participants));
    }

    public static ContractTask task(LocalDate from, LocalDate to, TaskParticipant... participants) {
        return new ContractTask(REQUEST, INSURANCE_CONTRACT, INSURER_POLICY, from, to, List.of(participants));
    }

    public static TaskParticipant insured(String amountDue) {
        return new TaskParticipant(0, INSURED, false, new BigDecimal(amountDue), person("Тестов", null));
    }

    public static TaskParticipant spouse(String amountDue) {
        return relative(1, SPOUSE, amountDue, "SPOUSE");
    }

    public static TaskParticipant child(String amountDue) {
        return relative(2, CHILD, amountDue, "CHILD");
    }

    public static TaskParticipant relative(int index, UUID id, String amountDue, @Nullable String relationType) {
        return new TaskParticipant(index, id, true, new BigDecimal(amountDue), person("Тестова", relationType));
    }

    public static ParticipantPerson person(String lastName, @Nullable String relationType) {
        return new ParticipantPerson(lastName, "Тест", "Тестович", LocalDate.parse("1984-03-11"), "MALE",
                relationType, document("RF_PASSPORT"), "Тестовый адрес");
    }

    public static ParticipantDocument document(String type) {
        return new ParticipantDocument(type, "4510", "123456", "Тестовый орган", LocalDate.parse("2010-04-20"),
                "770-001");
    }
}

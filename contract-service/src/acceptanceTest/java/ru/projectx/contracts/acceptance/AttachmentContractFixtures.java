package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import io.cucumber.spring.ScenarioScope;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.service.km.submission.KmContractSubmissionScheduler;

@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
class AttachmentContractFixtures {

    static final String REQUEST = "61a7982c-a0b4-4bbf-9527-60b40b752803";
    static final String PARTICIPANT = "9275471c-d2fe-484f-97a6-d6d7b1267b5e";
    static final String BEFORE_TASK = "до ДГ-01";

    private static final String HOLDER = "ae8312c3-350b-4d0b-806c-5e2460e74dbc";
    private static final String OTHER_REQUEST = "7f2c9e4a-1b3d-4e5f-8a6b-9c0d1e2f3a4b";
    private static final String OTHER_PARTICIPANT = "6778e8c2-985a-4d54-9bd3-0f6403b104d6";
    private static final String PROGRAM = "2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a";
    private static final String INSURANCE = "e12aab1a-1582-4b3a-af36-d30e637953f1";
    private static final String POLICY = "a23ba061-f742-4b20-8b95-36a0e525c087";

    private final TaskPublishingSteps tasks;
    private final InboxSteps inbox;
    private final AcceptanceSteps acceptance;
    private final KmContractPaymentSteps payments;
    private final KmContractSubmissionScheduler submissions;
    private final ReferenceStub references;
    private final AttachmentContractStates states;

    AttachmentContractFixtures(TaskPublishingSteps tasks, InboxSteps inbox, AcceptanceSteps acceptance,
            KmContractPaymentSteps payments, KmContractSubmissionScheduler submissions, ReferenceStub references,
            AttachmentContractStates states) {
        this.tasks = tasks;
        this.inbox = inbox;
        this.acceptance = acceptance;
        this.payments = payments;
        this.submissions = submissions;
        this.references = references;
        this.states = states;
    }

    void state(String state) throws Exception {
        switch (state) {
            case BEFORE_TASK -> { }
            case "DRAFT_READY" -> draft(REQUEST, PARTICIPANT);
            case "после ДГ-02" -> cancel();
            case "ACCEPTED" -> accept();
            case "INVOICE_ISSUED" -> invoice();
            case "PAID" -> pay();
            case "SUBMITTED", "ISSUED" -> submit(state);
            case "после чужих передач" -> unrelatedSubmissions();
            default -> throw new AssertionError("неизвестное состояние договора: " + state);
        }
    }

    UUID contract() {
        return states.id(REQUEST, PARTICIPANT);
    }

    void expectNoSubmittedDocuments() {
        states.expectNoSubmittedDocuments(contract());
    }

    void history(String history) {
        draft(REQUEST, PARTICIPANT);
        switch (history) {
            case "SUBMITTED", "ISSUED" -> states.force(contract(), history);
            case "ERROR после FAILED" -> recorded("ERROR", "FAILED");
            case "CANCELLED после записи NEW" -> recorded("CANCELLED", "NEW");
            case "CANCELLED после передачи PUBLISHED без тела" -> {
                recorded("CANCELLED", "PUBLISHED");
                states.clearSubmissionBody(contract());
            }
            case "PAID после возврата дежурным из ERROR" -> recorded("PAID", "FAILED");
            case "другой договор пары" -> {
                UUID current = contract();
                states.force(current, "PAID");
                states.recordSubmission(states.copyAs(current, PARTICIPANT, "CANCELLED"), "PUBLISHED");
                assertThat(states.submissions(current)).isZero();
            }
            default -> throw new AssertionError("неизвестная история договора: " + history);
        }
    }

    private void recorded(String status, String publication) {
        states.recordSubmission(contract(), publication);
        states.force(contract(), status);
        states.expect(REQUEST, PARTICIPANT, status);
    }

    private void draft(String request, String participant) {
        tasks.requestDefined(Map.of("заявка", request, "номер", "IZ-2026-STAND",
                "массовая заявка", "c9bfd8fc-7ed2-4fcc-a67f-b8afe814b36f",
                "договор страхования", INSURANCE, "полис страхователя", POLICY,
                "ЛПУ", "1b1b4fdf-bd2a-4055-a127-8c24c544a23f", "заезд", "2026-04-10", "выезд", "2026-04-24"));
        tasks.taskPublished(UUID.randomUUID().toString(), request, 1, List.of(
                Map.of("участник", HOLDER, "родственник", "нет", "доплата", "0.00"),
                Map.of("участник", participant, "родственник", "да", "доплата", "34000.00",
                        "родство", "SPOUSE", "программа", PROGRAM)));
        inbox.everythingProcessed();
        states.expect(request, participant, "DRAFT_READY");
    }

    private void cancel() {
        draft(REQUEST, PARTICIPANT);
        tasks.cancellationPublished(UUID.randomUUID().toString(), REQUEST, 2, Map.of(
                "аннулировано", "2026-02-10T07:05:00Z", "причина", "INSURED_REFUSED"));
        inbox.everythingProcessed();
        states.expect(REQUEST, PARTICIPANT, "CANCELLED");
    }

    private void accept() throws Exception {
        draft(REQUEST, PARTICIPANT);
        acceptance.acceptsContractOf(PARTICIPANT, REQUEST, Map.of(
                "contract_version", "1", "accepted_by", HOLDER, "email", "fixture@example.test",
                "accepted_at", "2026-02-10T07:04:58Z", "channel", "WEB"));
        acceptance.answeredAcceptance(200, Map.of("state", "ACCEPTED"));
        states.expect(REQUEST, PARTICIPANT, "ACCEPTED");
    }

    private void invoice() throws Exception {
        accept();
        states.force(contract(), "INVOICE_ISSUED");
        states.expect(REQUEST, PARTICIPANT, "INVOICE_ISSUED");
    }

    private void pay() throws Exception {
        accept();
        payments.contractPaid(PARTICIPANT, REQUEST, "2026-02-10T07:05:00Z");
        states.expect(REQUEST, PARTICIPANT, "PAID");
        defineReference(ReferenceKind.PROGRAM, PROGRAM, "6b02f1cc-9d47-4e35-8a20-7f1c3e5b09a2");
        defineReference(ReferenceKind.INSURANCE_CONTRACT, INSURANCE, "8c41e2ab-55d0-4a17-9b63-2f0e7c1d4a88");
        defineReference(ReferenceKind.POLICY, POLICY, "1d9a6e77-4c02-4b88-a15e-30f7b2c9de41");
    }

    private void submit(String status) throws Exception {
        pay();
        submissions.prepareDue();
        states.expect(REQUEST, PARTICIPANT, "SUBMITTED");
        assertThat(states.submissions(contract())).isEqualTo(1);
        if ("ISSUED".equals(status)) {
            states.force(contract(), status);
        }
        states.expect(REQUEST, PARTICIPANT, status);
    }

    private void unrelatedSubmissions() throws Exception {
        pay();
        UUID otherParticipant = states.copyAs(contract(), OTHER_PARTICIPANT, "ISSUED");
        states.recordSubmission(otherParticipant, "PUBLISHED");
        draft(OTHER_REQUEST, OTHER_PARTICIPANT);
        UUID otherRequest = states.copyAs(states.id(OTHER_REQUEST, OTHER_PARTICIPANT), PARTICIPANT, "ISSUED");
        states.recordSubmission(otherRequest, "PUBLISHED");
        states.expect(REQUEST, PARTICIPANT, "PAID");
        assertThat(states.submissions(contract())).isZero();
    }

    private void defineReference(ReferenceKind kind, String id, String external) {
        references.define(new ReferenceStub.Entry(kind, UUID.fromString(id), UUID.fromString(external), "СТЕНД", false));
    }
}

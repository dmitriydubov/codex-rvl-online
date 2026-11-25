package ru.projectx.contracts.acceptance;

import io.cucumber.java.ru.Дано;
import java.util.List;
import java.util.Map;

public class SubmittedLmPolicySteps {

    private static final String INITIAL_TASK = "6e36288d-3d13-4b73-99d1-f2994bb8261d";
    private static final String INSURED = "ae8312c3-350b-4d0b-806c-5e2460e74dbc";
    private static final String PROGRAM = "2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a";

    private final RequestFixture requests;
    private final ReferenceStubSteps reference;
    private final TaskPublishingSteps tasks;
    private final InboxSteps inbox;
    private final LmPolicyRequestSteps policies;
    private final IssuanceSteps issuance;

    public SubmittedLmPolicySteps(RequestFixture requests, ReferenceStubSteps reference, TaskPublishingSteps tasks,
            InboxSteps inbox, LmPolicyRequestSteps policies, IssuanceSteps issuance) {
        this.requests = requests;
        this.reference = reference;
        this.tasks = tasks;
        this.inbox = inbox;
        this.policies = policies;
        this.issuance = issuance;
    }

    @Дано("по заявке {string} застрахованный без доплаты и один родственник {string} с полисом ЛМ, ожидающим ответа 1С:УСК в статусе SUBMITTED")
    public void submittedPolicy(String requestId, String relative) {
        submitPolicy(requestId, relative, 1);
    }

    void submitPolicy(String requestId, String relative, int version) {
        defineIssuanceReferences(requestId);
        tasks.taskPublished(INITIAL_TASK, requestId, version, List.of(
                Map.of("участник", INSURED, "родственник", "нет", "доплата", "0.00", "программа", PROGRAM),
                Map.of("участник", relative, "родственник", "да", "доплата", "0.00", "родство", "CHILD",
                        "программа", PROGRAM)));
        inbox.processingPass();
        policies.preparationPass();
        issuance.latestIssuance(relative, requestId, Map.of("документ", "полис ЛМ", "статус", "SUBMITTED"));
    }

    private void defineIssuanceReferences(String requestId) {
        Map<String, String> request = requests.of(requestId);
        reference.programDefined(Map.of("id", PROGRAM,
                "external_id", "6b02f1cc-9d47-4e35-8a20-7f1c3e5b09a2", "в архиве", "false"));
        reference.insuranceContractDefined(Map.of("id", StepTables.required(request, "договор страхования"),
                "external_id", "8c41e2ab-55d0-4a17-9b63-2f0e7c1d4a88", "в архиве", "false"));
        reference.policyDefined(Map.of("id", StepTables.required(request, "полис страхователя"),
                "external_id", "1d9a6e77-4c02-4b88-a15e-30f7b2c9de41", "номер", "СТЕНД-0001", "в архиве", "false"));
    }
}

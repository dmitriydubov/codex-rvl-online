package ru.projectx.contracts.acceptance;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.util.Map;
import java.util.UUID;
import ru.projectx.contracts.exception.ReferenceKind;

public class ReferenceStubSteps {

    private final ReferenceStub stub;

    public ReferenceStubSteps(ReferenceStub stub) {
        this.stub = stub;
    }

    @Дано("у справочного сервиса есть программа:")
    public void programDefined(Map<String, String> requisites) {
        stub.define(entry(ReferenceKind.PROGRAM, requisites));
    }

    @Дано("у справочного сервиса есть договор страхования:")
    public void insuranceContractDefined(Map<String, String> requisites) {
        stub.define(entry(ReferenceKind.INSURANCE_CONTRACT, requisites));
    }

    @Дано("у справочного сервиса есть полис:")
    public void policyDefined(Map<String, String> requisites) {
        stub.define(entry(ReferenceKind.POLICY, requisites));
    }

    @Дано("полис появляется у справочного сервиса с {int}-го запроса по external_id:")
    public void policyAppearsAfter(int request, Map<String, String> requisites) {
        stub.appearsAfterNotFound(new ReferenceStub.Entry(ReferenceKind.POLICY, uuid(requisites, "id"),
                uuid(requisites, "external_id"), StepTables.required(requisites, "номер"), false), request - 1);
    }

    @Дано("запись справочника {string} с id {string} у справочного сервиса — {string}")
    public void recordState(String kindAlias, String id, String state) {
        ReferenceKind kind = ReferenceStub.kind(kindAlias);
        switch (state) {
            case "в архиве" -> stub.archive(kind, UUID.fromString(id), true);
            case "действует" -> stub.archive(kind, UUID.fromString(id), false);
            case "отсутствует" -> stub.remove(kind, UUID.fromString(id));
            default -> throw new IllegalArgumentException("неизвестное состояние записи справочника: " + state);
        }
    }

    @Дано("справочный сервис недоступен для справочника {string}")
    public void unavailableFor(String kindAlias) {
        stub.unavailable(ReferenceStub.kind(kindAlias));
    }

    @Дано("справочный сервис недоступен")
    public void unavailable() {
        stub.unavailableForAll();
    }

    @Когда("справочный сервис снова отвечает")
    public void availableAgain() {
        stub.available();
    }

    private static ReferenceStub.Entry entry(ReferenceKind kind, Map<String, String> requisites) {
        return new ReferenceStub.Entry(kind, uuid(requisites, "id"), uuid(requisites, "external_id"),
                requisites.get("номер"), Boolean.parseBoolean(StepTables.required(requisites, "в архиве")));
    }

    private static UUID uuid(Map<String, String> requisites, String key) {
        return UUID.fromString(StepTables.required(requisites, key));
    }
}

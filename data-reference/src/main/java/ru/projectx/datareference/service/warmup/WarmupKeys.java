package ru.projectx.datareference.service.warmup;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import ru.projectx.datareference.repository.ReferenceId;
import ru.projectx.datareference.repository.catalog.InsuranceContractKey;

public record WarmupKeys(
        List<InsuranceContractKey> insuranceContracts,
        List<ReferenceId> lpu,
        List<ReferenceId> programs,
        List<ReferenceId> policies) {

    private static final String API = "/internal/v1";
    private static final String INSURANCE_CONTRACTS = API + "/insurance-contracts";
    private static final String LPU = API + "/lpu/";

    public WarmupKeys {
        insuranceContracts = List.copyOf(insuranceContracts);
        lpu = List.copyOf(lpu);
        programs = List.copyOf(programs);
        policies = List.copyOf(policies);
    }

    public List<String> paths() {
        List<List<List<String>>> references = List.of(
                insuranceContracts.stream().map(WarmupKeys::insuranceContractPaths).toList(),
                lpu.stream().map(WarmupKeys::lpuPaths).toList(),
                programs.stream().map(program -> List.of(API + "/programs/" + program.id())).toList(),
                policies.stream().map(policy -> List.of(API + "/policies/" + policy.id() + "/contract-view")).toList());
        int rounds = references.stream().mapToInt(List::size).max().orElse(0);
        return IntStream.range(0, rounds).boxed()
                .flatMap(round -> references.stream()
                        .filter(reference -> round < reference.size())
                        .flatMap(reference -> reference.get(round).stream()))
                .toList();
    }

    private static List<String> insuranceContractPaths(InsuranceContractKey contract) {
        Stream<String> byContract = Stream.of(
                INSURANCE_CONTRACTS + "/" + contract.id(),
                INSURANCE_CONTRACTS + "?external_id=" + contract.externalId(),
                API + "/lpu?contract_external_id=" + contract.externalId());
        Stream<String> byInsurer = Stream.ofNullable(contract.externalInsurerId())
                .map(insurer -> INSURANCE_CONTRACTS + "?insurer_external_id=" + insurer);
        return Stream.concat(byContract, byInsurer).toList();
    }

    private static List<String> lpuPaths(ReferenceId sanatorium) {
        String card = LPU + sanatorium.id();
        return List.of(card, card + "/services", card + "/services?need_prices=true");
    }
}

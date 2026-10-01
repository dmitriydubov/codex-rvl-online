package ru.projectx.datareference.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.dto.PolicyResponse;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.entity.PolicyProgram;
import ru.projectx.datareference.entity.PolicyServiceLine;
import ru.projectx.datareference.entity.Program;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.repository.ProgramRepository;

/**
 * Полис целиком (R-05) — ручка сервиса Заявок: реквизиты, застрахованный, договор, программы РВЛ.
 * В пути наш {@code id}, внешний ключ — параметром; полис читается ещё и по номеру вместе
 * с договором: номер уникален в пределах договора, не глобально, а два полиса с одним номером —
 * конфликт данных 1С, {@code 409}.
 *
 * <p>Транзакция чтения здесь; ответ собирается внутри неё — за её границей ленивые
 * табличные части полиса уже не догрузятся ({@code open-in-view} выключен).
 */
@Service
public class PolicyQueryService implements PolicyReads {

    private final PolicyRepository policies;
    private final InsuranceContractRepository insuranceContracts;
    private final ProgramRepository programs;

    public PolicyQueryService(
            PolicyRepository policies, InsuranceContractRepository insuranceContracts, ProgramRepository programs) {

        this.policies = policies;
        this.insuranceContracts = insuranceContracts;
        this.programs = programs;
    }

    @Override
    @RestReadTransaction
    public PolicyResponse byId(UUID id) {
        return policies.findById(id)
                .map(this::assemble)
                .orElseThrow(() -> ReferenceNotFoundException.policy(id));
    }

    @Override
    @RestReadTransaction
    public PolicyResponse byExternalId(UUID externalId) {
        return policies.findByExternalId(externalId)
                .map(this::assemble)
                .orElseThrow(() -> ReferenceNotFoundException.policyByExternalId(externalId));
    }

    @Override
    @RestReadTransaction
    public PolicyResponse byNumber(String number, UUID contractId) {
        InsuranceContract contract = insuranceContracts.findById(contractId)
                .orElseThrow(() -> ReferenceNotFoundException.insuranceContract(contractId));
        List<Policy> found = policies.findAllByNumberAndExternalContractId(number, contract.externalId());
        if (found.size() > 1) {
            throw AmbiguousReferenceException.policy(found.stream().map(Policy::id).toList());
        }
        return found.stream().findFirst()
                .map(this::assemble)
                .orElseThrow(() -> ReferenceNotFoundException.policyByNumber(contractId));
    }

    /**
     * Договор и программы подтягиваются по GUID: обе связи логические, без FK (ТЗ-1),
     * и отсутствие записи в справочнике — норма, а не ошибка.
     */
    private PolicyResponse assemble(Policy policy) {
        InsuranceContract contract = insuranceContracts.findByExternalId(policy.externalContractId()).orElse(null);
        return PolicyResponse.of(policy, contract, rvlPrograms(policy));
    }

    /** Программы — одним запросом на все строки полиса и только действующие: архивная в ответ не попадает. */
    private Map<UUID, Program> rvlPrograms(Policy policy) {
        Set<UUID> referenced = Stream.concat(
                        policy.programs().stream().map(PolicyProgram::externalProgramId),
                        policy.serviceLines().stream().map(PolicyServiceLine::externalProgramId))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (referenced.isEmpty()) {
            return Map.of();
        }
        return programs.findAllByExternalIdIn(referenced).stream()
                .filter(program -> !program.isArchive())
                .collect(Collectors.toMap(Program::externalId, Function.identity()));
    }
}

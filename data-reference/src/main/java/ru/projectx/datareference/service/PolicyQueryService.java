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
 * Полис целиком для сервиса Заявок: застрахованный, договор страхования и программы РВЛ с условиями обслуживания.
 * Ответ собирается внутри транзакции, потому что табличные части грузятся лениво; отказ базы на ленивой догрузке
 * переводит в 503 обработчик ошибок REST.
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

    /**
     * Договор не читается: полис может прийти из 1С раньше договора, поэтому неизвестный договор означает то же,
     * что и отсутствие полиса. Номер уникален только в договоре, и два полиса с ним означают конфликт данных 1С.
     */
    @Override
    @RestReadTransaction
    public PolicyResponse byNumber(String number, UUID contractExternalId) {
        List<Policy> found = policies.findAllByNumberAndExternalContractId(number, contractExternalId);
        if (found.size() > 1) {
            throw AmbiguousReferenceException.policy(found.stream().map(Policy::id).toList());
        }
        return found.stream().findFirst()
                .map(this::assemble)
                .orElseThrow(() -> ReferenceNotFoundException.policyByNumber(contractExternalId));
    }

    /** Договор и программы связаны логически, по GUID 1С, и отсутствие записи в справочнике не ошибка. */
    private PolicyResponse assemble(Policy policy) {
        InsuranceContract contract = insuranceContracts.findByExternalId(policy.details().externalContractId())
                .orElse(null);
        return PolicyResponse.of(policy, contract, rvlPrograms(policy));
    }

    /** Программы читаются одним запросом на все строки полиса; архивная в ответ не попадает. */
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

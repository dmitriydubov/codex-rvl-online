package ru.projectx.datareference.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.dto.InsuranceContractResponse;
import ru.projectx.datareference.dto.PolicyResponse;
import ru.projectx.datareference.dto.ProgramResponse;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.Insurer;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.entity.PolicyProgram;
import ru.projectx.datareference.entity.PolicyServiceLine;
import ru.projectx.datareference.entity.Program;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.InsurerRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.repository.ProgramRepository;

/**
 * Чтение справочников по ключу: программа, полис, договор страхования.
 *
 * <p>В пути всегда наш {@code id}, внешний ключ — параметром (решение от 23.09), и ответ
 * несёт оба: потребители дальше передают GUID 1С в 1С:УСК сами и у себя не дублируют.
 * Полис читается ещё и по номеру вместе с договором: номер уникален в пределах договора,
 * не глобально, а два полиса с одним номером — конфликт данных 1С, {@code 409}.
 *
 * <p>Транзакция здесь, а не в контроллере, и {@code readOnly}: чтение не должно
 * случайно стать записью. Ответ собирается внутри транзакции — за её границей ленивые
 * связи уже не догрузятся ({@code open-in-view} выключен).
 */
@Service
public class ReferenceQueryService {

    private final ProgramRepository programs;
    private final PolicyRepository policies;
    private final InsuranceContractRepository insuranceContracts;
    private final InsurerRepository insurers;

    public ReferenceQueryService(
            ProgramRepository programs,
            PolicyRepository policies,
            InsuranceContractRepository insuranceContracts,
            InsurerRepository insurers) {

        this.programs = programs;
        this.policies = policies;
        this.insuranceContracts = insuranceContracts;
        this.insurers = insurers;
    }

    @Transactional(readOnly = true)
    public ProgramResponse program(UUID id) {
        return programs.findWithServicesById(id)
                .map(ProgramResponse::of)
                .orElseThrow(() -> ReferenceNotFoundException.program(id));
    }

    @Transactional(readOnly = true)
    public PolicyResponse policy(UUID id) {
        return policies.findById(id)
                .map(this::assemble)
                .orElseThrow(() -> ReferenceNotFoundException.policy(id));
    }

    @Transactional(readOnly = true)
    public PolicyResponse policyByExternalId(UUID externalId) {
        return policies.findByExternalId(externalId)
                .map(this::assemble)
                .orElseThrow(() -> ReferenceNotFoundException.policyByExternalId(externalId));
    }

    @Transactional(readOnly = true)
    public PolicyResponse policyByNumber(String number, UUID contractId) {
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

    @Transactional(readOnly = true)
    public InsuranceContractResponse insuranceContract(UUID id) {
        InsuranceContract contract = insuranceContracts.findById(id)
                .orElseThrow(() -> ReferenceNotFoundException.insuranceContract(id));
        return InsuranceContractResponse.of(contract, insurerOf(contract));
    }

    @Transactional(readOnly = true)
    public InsuranceContractResponse insuranceContractByExternalId(UUID externalId) {
        InsuranceContract contract = insuranceContracts.findByExternalId(externalId)
                .orElseThrow(() -> ReferenceNotFoundException.insuranceContractByExternalId(externalId));
        return InsuranceContractResponse.of(contract, insurerOf(contract));
    }

    /**
     * Договор и программы подтягиваются по GUID: обе связи логические, без FK (ТЗ-1),
     * и отсутствие записи в справочнике — норма, а не ошибка. Программы — одним запросом
     * на все строки полиса и только действующие: архивная программа в ответ не попадает.
     */
    private PolicyResponse assemble(Policy policy) {
        InsuranceContract contract = insuranceContracts.findByExternalId(policy.externalContractId()).orElse(null);
        return PolicyResponse.of(policy, contract, rvlPrograms(policy));
    }

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

    /**
     * Страхователь подтягивается вторым запросом по {@code external_insurer_id}: связь
     * логическая, без FK (ТЗ-2), и страхователя в справочнике может не быть вовсе.
     */
    @Nullable
    private Insurer insurerOf(InsuranceContract contract) {
        return Optional.ofNullable(contract.externalInsurerId())
                .flatMap(insurers::findByExternalId)
                .orElse(null);
    }
}

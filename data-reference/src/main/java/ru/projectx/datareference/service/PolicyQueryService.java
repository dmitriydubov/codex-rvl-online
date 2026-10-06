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
 * Чтение полиса целиком для сервиса Заявок: реквизиты, застрахованный, договор страхования и
 * программы РВЛ с условиями обслуживания. Полис находится по нашему идентификатору, по GUID 1С
 * или по номеру вместе с договором. Номер уникален только в пределах договора, поэтому два полиса
 * с одним номером в одном договоре — конфликт данных 1С, и ответ на такой запрос — 409.
 *
 * <p>Транзакция чтения открывается здесь, и ответ собирается внутри неё: табличные части полиса
 * загружаются лениво, а за границей транзакции они уже недоступны. Ленивая догрузка идёт мимо
 * репозитория, поэтому отказ базы на ней приходит сырым исключением Hibernate. Переводить его
 * здесь не нужно: обработчик ошибок REST переводит такие исключения сам и отвечает 503, как и на
 * отказ внутри репозитория.
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
     * Полис ищется по номеру и GUID договора страхования сразу, без чтения самого договора: связь полиса с
     * договором логическая, и полис может прийти из 1С раньше своего договора. Поэтому и неизвестный договор,
     * и отсутствие полиса в нём означают одно — полиса с таким номером в этом договоре нет.
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

    /**
     * Договор и программы подтягиваются по GUID 1С: обе связи логические, без внешнего ключа (ТЗ-1),
     * и отсутствие записи в справочнике — норма, а не ошибка.
     */
    private PolicyResponse assemble(Policy policy) {
        InsuranceContract contract = insuranceContracts.findByExternalId(policy.details().externalContractId())
                .orElse(null);
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

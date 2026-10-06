package ru.projectx.datareference.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.entity.PolicyProgram;
import ru.projectx.datareference.entity.PolicyServiceLine;
import ru.projectx.datareference.entity.Program;

/**
 * Полис целиком для сервиса Заявок (R-05): реквизиты, застрахованный, договор, программы РВЛ
 * с условиями обслуживания. Вместе с поиском полисов это единственные ответы сервиса
 * с персональными данными (ПДн). ПДн не пишутся в журнал ни в тексте сообщения, ни в тексте
 * исключения, а сервису Договоров полис отдаётся отдельным ответом без них.
 *
 * <p>Программы — только те, что есть в справочнике {@code programs} и не в архиве:
 * связь по GUID логическая, и полис может ссылаться на программу не-РВЛ, которую
 * справочник не хранит намеренно. Условия обслуживания вложены в свою программу
 * по паре (полис, программа). Сроки и {@code availability} отдаются как есть, без
 * фильтра по дате: вызывающему бывает нужен и будущий период.
 *
 * <p>Лимит и процент сооплаты — как есть: расчёт стоимости и сооплаты остаётся в ЛК HR.
 * Паспорта нет: {@code policy_passports} ни одним потоком не наполняется.
 *
 * @param contract {@code null}, если договора страхования нет в справочнике: связь логическая
 * @param endDate {@code null} — открытая дата источника
 */
public record PolicyResponse(
        UUID id,
        UUID externalId,
        String number,
        String lastName,
        String firstName,
        @Nullable String middleName,
        LocalDate dateOfBirth,
        @Nullable String gender,
        @Nullable String email,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        boolean availability,
        boolean isArchive,
        @Nullable ContractRef contract,
        List<ProgramLine> programs) {

    public static PolicyResponse of(
            Policy policy, @Nullable InsuranceContract contract, Map<UUID, Program> rvlPrograms) {

        List<ProgramLine> programs = policy.programs().stream()
                .filter(row -> rvlPrograms.containsKey(row.externalProgramId()))
                .map(row -> ProgramLine.of(row, rvlPrograms.get(row.externalProgramId()), policy.serviceLines()))
                .toList();
        Policy.Details details = policy.details();
        return new PolicyResponse(
                policy.id(), policy.externalId(), details.number(),
                details.lastName(), details.firstName(), details.middleName(),
                details.dateOfBirth(), details.gender(), details.email(),
                details.beginDate(), details.endDate(), details.availability(), policy.isArchive(),
                contract == null ? null : ContractRef.of(contract), programs);
    }

    /** Договор страхования, к которому относится полис. */
    public record ContractRef(UUID id, UUID externalId, String number) {

        static ContractRef of(InsuranceContract contract) {
            return new ContractRef(contract.id(), contract.externalId(), contract.number());
        }
    }

    /** Программа полиса ({@code policy_programs}) с условиями обслуживания по ней. */
    public record ProgramLine(
            NamedRef program,
            BigDecimal limitAmount,
            LocalDate beginDate,
            @Nullable LocalDate endDate,
            boolean availability,
            List<ServiceLine> services) {

        static ProgramLine of(PolicyProgram row, Program program, List<PolicyServiceLine> serviceLines) {
            List<ServiceLine> services = serviceLines.stream()
                    .filter(line -> row.externalProgramId().equals(line.externalProgramId()))
                    .map(ServiceLine::of)
                    .toList();
            return new ProgramLine(NamedRef.of(program, program.name()), row.limitAmount(), row.beginDate(), row.endDate(),
                    row.availability(), services);
        }
    }


    /** Условие обслуживания ({@code policy_services}): слой, процент сооплаты, срок. */
    public record ServiceLine(
            int layerNumber,
            BigDecimal copayment,
            @Nullable LocalDate beginDate,
            @Nullable LocalDate endDate,
            boolean availability) {

        static ServiceLine of(PolicyServiceLine line) {
            return new ServiceLine(line.layerNumber(), line.copayment(), line.beginDate(), line.endDate(),
                    line.availability());
        }
    }
}

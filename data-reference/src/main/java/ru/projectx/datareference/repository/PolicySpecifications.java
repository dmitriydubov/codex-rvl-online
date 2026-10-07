package ru.projectx.datareference.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.entity.PolicyServiceLine;
import ru.projectx.datareference.entity.Program;

/**
 * Условия отбора полисов на Criteria API. Поиск для подсказки кадровику собирается на ходу, потому что число слов
 * ФИО заранее неизвестно. Нативного SQL здесь нет намеренно: запрос должен читаться без знания диалекта PostgreSQL.
 *
 * <p>Здесь же единственное место правила «действует сегодня» для полиса и для строки условий обслуживания. Его
 * используют и поиск полисов, и отбор программ для каталога учреждений. Полис действует, если он не в архиве и
 * сегодня попадает в его срок. Строка условий действует, если она доступна, сегодня попадает в её срок и её
 * программа есть в справочнике и не в архиве. Границы срока входят в него; пустая дата начала или окончания срок
 * с этой стороны не ограничивает. Связь строки с программой логическая, по GUID 1С, и это равенство служит фильтром
 * «только восстановительное лечение»: в справочнике программ лежат только такие программы.
 *
 * <p>Символ экранирования в {@code like} — {@code !}; экранирует вызывающий сервис.
 */
public final class PolicySpecifications {

    private static final char ESCAPE = '!';
    private static final String YO = "ё";
    private static final String YE = "е";

    private PolicySpecifications() {
    }

    /** Действующие полисы договора, у которых есть действующая строка условий по программе из справочника. */
    public static Specification<Policy> activeRvlPoliciesOf(UUID contractExternalId, LocalDate today) {
        return (policy, query, cb) -> cb.and(
                activePolicyOf(cb, policy, contractExternalId, today),
                cb.exists(rvlServiceLine(policy, requireQuery(query), cb, today)));
    }

    /** Полис договора {@code contractExternalId}, который действует в день {@code today}. */
    static Predicate activePolicyOf(CriteriaBuilder cb, Path<Policy> policy, UUID contractExternalId, LocalDate today) {
        return cb.and(
                cb.equal(policy.get("externalContractId"), contractExternalId),
                cb.isFalse(policy.<Boolean>get("archive")),
                cb.lessThanOrEqualTo(policy.<LocalDate>get("beginDate"), today),
                openOrAfter(cb, policy.get("endDate"), today));
    }

    /** Строка условий, которая действует в день {@code today}, по программе {@code program} из справочника. */
    static Predicate activeRvlServiceLine(
            CriteriaBuilder cb, Path<PolicyServiceLine> line, Path<Program> program, LocalDate today) {

        return cb.and(
                cb.isTrue(line.<Boolean>get("availability")),
                openOrBefore(cb, line.get("beginDate"), today),
                openOrAfter(cb, line.get("endDate"), today),
                cb.equal(program.get("externalId"), line.get("externalProgramId")),
                cb.isFalse(program.<Boolean>get("archive")));
    }

    /** Номер: {@code pattern} — уже экранированная подстрока в нижнем регистре, с {@code %} по краям. */
    public static Specification<Policy> numberContains(String pattern) {
        return (policy, query, cb) -> like(cb, cb.lower(policy.get("number")), pattern);
    }

    /**
     * ФИО: каждый шаблон-префикс (экранированный токен с {@code %} в конце) подходит к фамилии,
     * имени или отчеству; {@code ё} с обеих сторон читается как {@code е}.
     */
    public static Specification<Policy> namesPrefixedBy(List<String> prefixes) {
        return (policy, query, cb) -> cb.and(prefixes.stream()
                .map(prefix -> cb.or(
                        like(cb, normalized(cb, policy.get("lastName")), prefix),
                        like(cb, normalized(cb, policy.get("firstName")), prefix),
                        like(cb, normalized(cb, cb.coalesce(policy.<String>get("middleName"), "")), prefix)))
                .toArray(Predicate[]::new));
    }

    /** Имя в нижнем регистре, {@code ё} читается как {@code е} — так нормализована и строка поиска. */
    static Expression<String> normalized(CriteriaBuilder cb, Expression<String> name) {
        return cb.function("replace", String.class, cb.lower(name), cb.literal(YO), cb.literal(YE));
    }

    /** {@code like} с символом экранирования {@code !}, которым экранирует вызывающий сервис. */
    static Predicate like(CriteriaBuilder cb, Expression<String> value, String pattern) {
        return cb.like(value, pattern, ESCAPE);
    }

    private static Subquery<UUID> rvlServiceLine(
            Root<Policy> policy, CriteriaQuery<?> query, CriteriaBuilder cb, LocalDate today) {

        Subquery<UUID> lines = query.subquery(UUID.class);
        Root<PolicyServiceLine> line = lines.from(PolicyServiceLine.class);
        Root<Program> program = lines.from(Program.class);
        return lines.select(line.get("id")).where(
                cb.equal(line.get("policy"), policy),
                activeRvlServiceLine(cb, line, program, today));
    }

    private static Predicate openOrBefore(CriteriaBuilder cb, Path<LocalDate> date, LocalDate today) {
        return cb.or(cb.isNull(date), cb.lessThanOrEqualTo(date, today));
    }

    private static Predicate openOrAfter(CriteriaBuilder cb, Path<LocalDate> date, LocalDate today) {
        return cb.or(cb.isNull(date), cb.greaterThanOrEqualTo(date, today));
    }

    private static CriteriaQuery<?> requireQuery(CriteriaQuery<?> query) {
        return Objects.requireNonNull(query, "подзапрос строится только в запросе выборки");
    }
}

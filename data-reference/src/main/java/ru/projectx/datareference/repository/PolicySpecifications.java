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

public final class PolicySpecifications {

    private static final char ESCAPE = '!';
    private static final String YO = "ё";
    private static final String YE = "е";

    private PolicySpecifications() {
    }

    public static Specification<Policy> activeRvlPoliciesOf(UUID contractExternalId, LocalDate today) {
        return (policy, query, cb) -> cb.and(
                activePolicyOf(cb, policy, contractExternalId, today),
                cb.exists(rvlServiceLine(policy, requireQuery(query), cb, today)));
    }

    static Predicate activePolicyOf(CriteriaBuilder cb, Path<Policy> policy, UUID contractExternalId, LocalDate today) {
        return cb.and(
                cb.equal(policy.get("externalContractId"), contractExternalId),
                cb.isFalse(policy.<Boolean>get("archive")),
                cb.lessThanOrEqualTo(policy.<LocalDate>get("beginDate"), today),
                openOrAfter(cb, policy.get("endDate"), today));
    }

    static Predicate activeRvlServiceLine(
            CriteriaBuilder cb, Path<PolicyServiceLine> line, Path<Program> program, LocalDate today) {

        return cb.and(
                cb.isTrue(line.<Boolean>get("availability")),
                openOrBefore(cb, line.get("beginDate"), today),
                openOrAfter(cb, line.get("endDate"), today),
                cb.equal(program.get("externalId"), line.get("externalProgramId")),
                cb.isFalse(program.<Boolean>get("archive")));
    }

    public static Specification<Policy> numberContains(String pattern) {
        return (policy, query, cb) -> like(cb, cb.lower(policy.get("number")), pattern);
    }

    public static Specification<Policy> namesPrefixedBy(List<String> prefixes) {
        return (policy, query, cb) -> cb.and(prefixes.stream()
                .map(prefix -> cb.or(
                        like(cb, normalized(cb, policy.get("lastName")), prefix),
                        like(cb, normalized(cb, policy.get("firstName")), prefix),
                        like(cb, normalized(cb, cb.coalesce(policy.<String>get("middleName"), "")), prefix)))
                .toArray(Predicate[]::new));
    }

    static Expression<String> normalized(CriteriaBuilder cb, Expression<String> name) {
        return cb.function("replace", String.class, cb.lower(name), cb.literal(YO), cb.literal(YE));
    }

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

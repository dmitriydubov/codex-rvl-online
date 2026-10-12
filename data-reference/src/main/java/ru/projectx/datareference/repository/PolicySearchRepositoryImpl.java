package ru.projectx.datareference.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import ru.projectx.datareference.entity.Policy;

class PolicySearchRepositoryImpl implements PolicySearchRepository {

    private static final String NUMBER = "number";
    private static final String LAST_NAME = "lastName";
    private static final String FIRST_NAME = "firstName";
    private static final String ID = "id";

    private final EntityManager entityManager;

    PolicySearchRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<Policy> findRankedByNumber(
            PolicyScope scope, String number, String numberPrefix, String numberSubstring) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Policy> query = cb.createQuery(Policy.class);
        Root<Policy> policy = query.from(Policy.class);
        Expression<String> lowerNumber = cb.lower(policy.get(NUMBER));
        query.select(policy)
                .where(inScope(scope, PolicySpecifications.numberContains(numberSubstring)).toPredicate(policy, query, cb))
                .orderBy(
                        cb.desc(cb.equal(lowerNumber, number)),
                        cb.desc(PolicySpecifications.like(cb, lowerNumber, numberPrefix)),
                        cb.asc(policy.get(LAST_NAME)),
                        cb.asc(policy.get(FIRST_NAME)),
                        cb.asc(policy.get(ID)));
        return limited(query, scope);
    }

    @Override
    public List<Policy> findRankedByNames(PolicyScope scope, List<String> namePrefixes) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Policy> query = cb.createQuery(Policy.class);
        Root<Policy> policy = query.from(Policy.class);
        Predicate lastName = anyPrefix(cb, policy.get(LAST_NAME), namePrefixes);
        Predicate firstName = anyPrefix(cb, policy.get(FIRST_NAME), namePrefixes);
        query.select(policy)
                .where(inScope(scope, PolicySpecifications.namesPrefixedBy(namePrefixes)).toPredicate(policy, query, cb))
                .orderBy(
                        cb.desc(cb.and(lastName, firstName)),
                        cb.desc(lastName),
                        cb.asc(policy.get(LAST_NAME)),
                        cb.asc(policy.get(FIRST_NAME)),
                        cb.asc(policy.get(ID)));
        return limited(query, scope);
    }

    private static Specification<Policy> inScope(PolicyScope scope, Specification<Policy> matching) {
        return Specification.allOf(
                PolicySpecifications.activeRvlPoliciesOf(scope.contractExternalId(), scope.today()), matching);
    }

    private List<Policy> limited(CriteriaQuery<Policy> query, PolicyScope scope) {
        return entityManager.createQuery(query).setMaxResults(scope.limit()).getResultList();
    }

    private static Predicate anyPrefix(CriteriaBuilder cb, Expression<String> name, List<String> prefixes) {
        Expression<String> normalized = PolicySpecifications.normalized(cb, cb.trim(name));
        return cb.or(prefixes.stream()
                .map(prefix -> PolicySpecifications.like(cb, normalized, prefix))
                .toArray(Predicate[]::new));
    }
}

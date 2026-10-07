package ru.projectx.datareference.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.entity.PolicyServiceLine;
import ru.projectx.datareference.entity.Program;

/**
 * Реализация {@link ActivePolicyProgramsRepository} на Criteria API: условия «действует» берутся из
 * {@link PolicySpecifications}, чтобы у каталога и у поиска полисов было одно правило, а не две его копии.
 */
class ActivePolicyProgramsRepositoryImpl implements ActivePolicyProgramsRepository {

    private final EntityManager entityManager;

    ActivePolicyProgramsRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<UUID> findRvlProgramExternalIdsOfActivePolicies(UUID contractExternalId, LocalDate today) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<UUID> query = cb.createQuery(UUID.class);
        Root<PolicyServiceLine> line = query.from(PolicyServiceLine.class);
        Join<PolicyServiceLine, Policy> policy = line.join("policy");
        Root<Program> program = query.from(Program.class);
        query.select(line.get("externalProgramId")).distinct(true).where(
                PolicySpecifications.activePolicyOf(cb, policy, contractExternalId, today),
                PolicySpecifications.activeRvlServiceLine(cb, line, program, today));
        return entityManager.createQuery(query).getResultList();
    }
}

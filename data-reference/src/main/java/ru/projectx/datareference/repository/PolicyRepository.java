package ru.projectx.datareference.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.Policy;

/**
 * Полисы застрахованных. Единственный репозиторий сервиса, за которым лежат
 * персональные данные.
 *
 * <p>Поиск для R-04 — фрагмент {@link PolicySearchRepository}: отбор по условиям
 * {@link PolicySpecifications}, ранжирование и предел выдачи — одним запросом в базе (Ш12):
 * число токенов ФИО заранее неизвестно, и статическим запросом их не выразить.
 */
@Repository
public interface PolicyRepository extends JpaRepository<Policy, UUID>, PolicySearchRepository {

    Optional<Policy> findByExternalId(UUID externalId);

    /** Номер полиса уникален в пределах договора, не глобально; больше одной строки — конфликт данных 1С. */
    List<Policy> findAllByNumberAndExternalContractId(String number, UUID externalContractId);

    /**
     * Базовая логика A для каталога (R-01): программы РВЛ, которые дают действующие
     * полисы договора, — GUID программ из действующих условий обслуживания, найденные
     * в справочнике программ. Правило то же, что в {@link PolicySpecifications#activeRvlPoliciesOf}
     * для R-04, на выходе только программы, а не полисы. Равенство с {@code programs}
     * по GUID — и есть фильтр «только РВЛ»: в справочнике лежат только программы РВЛ,
     * и вырезать его при оптимизации нельзя (ответ на вопрос 12 ТЗ).
     */
    @Query("""
            select distinct line.externalProgramId
              from PolicyServiceLine line
              join line.policy policy, Program program
             where policy.externalContractId = :contractExternalId
               and policy.archive = false
               and policy.beginDate <= :today
               and (policy.endDate is null or policy.endDate >= :today)
               and line.availability = true
               and (line.endDate is null or line.endDate >= :today)
               and program.externalId = line.externalProgramId
               and program.archive = false
            """)
    List<UUID> findRvlProgramExternalIdsOfActivePolicies(UUID contractExternalId, LocalDate today);
}

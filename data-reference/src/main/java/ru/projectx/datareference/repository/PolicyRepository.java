package ru.projectx.datareference.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.Policy;

/**
 * Полисы застрахованных. Единственный репозиторий сервиса, за которым лежат
 * персональные данные.
 *
 * <p>Поиск полисов для подсказки кадровику лежит во фрагменте {@link PolicySearchRepository}.
 * Отбор по условиям {@link PolicySpecifications}, ранжирование и предел выдачи делаются одним
 * запросом в базе. Запрос собирается на ходу, потому что число слов ФИО в строке поиска заранее
 * неизвестно и статическим запросом его не выразить.
 */
@Repository
public interface PolicyRepository extends JpaRepository<Policy, UUID>, PolicySearchRepository {

    Optional<Policy> findByExternalId(UUID externalId);

    /** Номер полиса уникален в пределах договора, не глобально; больше одной строки — конфликт данных 1С. */
    List<Policy> findAllByNumberAndExternalContractId(String number, UUID externalContractId);

    /**
     * Программы РВЛ для каталога санаториев по договору страхования: программы, которые дают действующие
     * полисы договора, — GUID программ из действующих условий обслуживания, найденные
     * в справочнике программ. Правило то же, что в {@link PolicySpecifications#activeRvlPoliciesOf}
     * для поиска полисов, на выходе только программы, а не полисы. Равенство с {@code programs}
     * по GUID — и есть фильтр «только РВЛ»: в справочнике лежат только программы РВЛ,
     * и вырезать его при оптимизации нельзя.
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

    /**
     * Сколько полисов пережили границу хранения персональных данных, а данные ещё не стёрты. Отбор тот же, что у
     * {@link #erasePersonalDataBeyond}, слово в слово: так обоим запросам подходят одни частичные индексы.
     */
    @Query(nativeQuery = true, value = """
            select count(*)
              from policies
             where personal_data_erased_at is null
               and (end_date < :endedBefore or archived_at <= :archivedBy)
            """)
    long countPersonalDataBeyond(@Param("endedBefore") LocalDate endedBefore, @Param("archivedBy") Instant archivedBy);

    /**
     * Стирает ФИО, дату рождения и почту не больше чем у {@code limit} полисов, у которых срок действия кончился раньше
     * дня {@code endedBefore} или которые переведены в архив не позже {@code archivedBy}, и возвращает, у скольких
     * стёрто. Транзакцию открывает вызывающий, отдельную на каждую пачку.
     *
     * <p>Отбор повторён и снаружи подзапроса: если в это время разбор применил к полису новое сообщение, например 1С
     * сняла его с архива, PostgreSQL перепроверит внешнее условие на новой версии строки и данные не сотрёт. Запрос на
     * SQL PostgreSQL: в языке запросов JPA у подзапроса нет ограничения числа строк.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            update policies
               set last_name = null, first_name = null, middle_name = null, date_of_birth = null, email = null,
                   personal_data_erased_at = :now
             where personal_data_erased_at is null
               and (end_date < :endedBefore or archived_at <= :archivedBy)
               and id in (select id
                            from policies
                           where personal_data_erased_at is null
                             and (end_date < :endedBefore or archived_at <= :archivedBy)
                           limit :limit)
            """)
    int erasePersonalDataBeyond(@Param("endedBefore") LocalDate endedBefore, @Param("archivedBy") Instant archivedBy,
                                @Param("now") Instant now, @Param("limit") int limit);
}

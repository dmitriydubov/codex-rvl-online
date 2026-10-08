package ru.projectx.datareference.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.LpuContract;

/**
 * Upsert идёт по {@code external_id} — GUID объекта в 1С, а не по нашему первичному
 * ключу: другого устойчивого идентификатора у сообщения источника нет.
 */
@Repository
public interface LpuContractRepository extends JpaRepository<LpuContract, UUID> {

    Optional<LpuContract> findByExternalId(UUID externalId);

    /**
     * Договоры учреждений, действующие на {@code today}, вместе с учреждением, одним запросом на весь каталог. Ещё не
     * начавшийся договор не берётся: иначе загруженный заранее договор следующего года делал бы выбор прайсового
     * договора неоднозначным до конца текущего.
     */
    @Query("""
            select contract from LpuContract contract
              join fetch contract.lpu
             where contract.lpu.id in :lpuIds
               and contract.archive = false
               and contract.beginDate <= :today
               and (contract.endDate is null or contract.endDate >= :today)
            """)
    List<LpuContract> findActiveByLpuIdIn(Collection<UUID> lpuIds, LocalDate today);
}

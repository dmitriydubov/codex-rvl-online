package ru.projectx.datareference.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.PriceListFinalization;

/** Последняя применённая передача прайса по каждой услуге, таблица {@code price_list_finalization}. */
@Repository
public interface PriceListFinalizationRepository extends JpaRepository<PriceListFinalization, UUID> {

    /**
     * Строка услуги, заблокированная до конца транзакции вызывающего. Сборка передачи и закрытие зависших передач
     * берут её первой, поэтому за одну услугу они решают по очереди. Блокировку ждут не дольше таймаута блокировок
     * пула, после него отказ временный.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select finalization from PriceListFinalization finalization "
            + "where finalization.serviceExternalId = :serviceExternalId")
    Optional<PriceListFinalization> lockByService(@Param("serviceExternalId") UUID serviceExternalId);
}

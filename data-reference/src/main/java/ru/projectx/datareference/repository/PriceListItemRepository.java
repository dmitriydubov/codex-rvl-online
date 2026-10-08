package ru.projectx.datareference.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.PriceListItem;

/** Цены услуг. Собственного {@code external_id} у строк нет — они замещаются по услуге. */
@Repository
public interface PriceListItemRepository extends JpaRepository<PriceListItem, UUID> {

    /**
     * Снимает прежний прайс по услуге целиком одним запросом. {@code flushAutomatically} нужен, чтобы удаление увидело
     * записанное раньше в этой транзакции, а {@code clearAutomatically} нельзя: очистка отцепила бы строки сырья,
     * статусы которых сборщик проставляет рядом.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from PriceListItem item where item.service.id = :serviceId")
    int deleteByServiceId(@Param("serviceId") UUID serviceId);

    /**
     * Актуальные цены всех услуг договора одним запросом: строки, срок которых не кончился. Будущие сроки отдаются,
     * потому что вызывающему бывает нужен заезд следующего сезона.
     */
    @Query("""
            select item from PriceListItem item
              join fetch item.service
             where item.service.id in :serviceIds
               and (item.validTo is null or item.validTo >= :today)
             order by item.validFrom
            """)
    List<PriceListItem> findCurrentByServiceIdIn(Collection<UUID> serviceIds, LocalDate today);

    /**
     * Строки прайса услуги, действующие хотя бы на одни сутки проживания: с {@code checkIn} включительно до
     * {@code checkOut} не включительно. Строка действует по {@code valid_to} включительно.
     */
    @Query("""
            select item from PriceListItem item
             where item.service.id = :serviceId
               and item.validFrom < :checkOut
               and (item.validTo is null or item.validTo >= :checkIn)
             order by item.validFrom
            """)
    List<PriceListItem> findWithinPeriodByServiceId(UUID serviceId, LocalDate checkIn, LocalDate checkOut);
}

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
     * Снимает прежний прайс по услуге целиком.
     *
     * <p>Массовым запросом, а не выборкой с последующим удалением по одной: срез
     * по услуге — это десятки строк, а смысл операции («убрать прежний прайс целиком»)
     * выражается одним запросом честнее, чем циклом.
     *
     * <p>{@code flushAutomatically} нужен: удаление обязано увидеть всё, что записано
     * в этой же транзакции до него. {@code clearAutomatically} — нет: очистка отцепила бы
     * от контекста строки сырья, статусы которых сборщик проставляет рядом.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from PriceListItem item where item.service.id = :serviceId")
    int deleteByServiceId(@Param("serviceId") UUID serviceId);

    /**
     * Актуальные цены услуг для R-03, одним запросом на все услуги договора: строки,
     * чей период ещё не закончился, — открытая {@code valid_to} читается как
     * бесконечность (В-4). Будущие периоды отдаются, вызывающему бывает нужен заезд
     * следующего сезона; прошедшие — нет. Порядок — по началу периода, как в ответе.
     */
    @Query("""
            select item from PriceListItem item
              join fetch item.service
             where item.service.id in :serviceIds
               and (item.validTo is null or item.validTo >= :today)
             order by item.validFrom
            """)
    List<PriceListItem> findCurrentByServiceIdIn(Collection<UUID> serviceIds, LocalDate today);
}

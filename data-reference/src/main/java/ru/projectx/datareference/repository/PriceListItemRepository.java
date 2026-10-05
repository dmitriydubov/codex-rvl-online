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
     * Актуальные цены услуг для перечня услуг ЛПУ, одним запросом на все услуги договора: строки,
     * срок которых ещё не закончился. Пустой {@code valid_to} значит «бессрочно». Будущие сроки
     * отдаются, потому что вызывающему бывает нужен заезд следующего сезона, а прошедшие — нет.
     * Порядок — по началу срока, как в ответе.
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
     * Строки прайса услуги, которые действуют хотя бы на одни сутки периода проживания, одним запросом на весь
     * период. Сутки периода идут с {@code checkIn} включительно до {@code checkOut} не включительно, а строка
     * действует по {@code valid_to} включительно; пустой {@code valid_to} значит «бессрочно». Порядок — по началу
     * срока действия.
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

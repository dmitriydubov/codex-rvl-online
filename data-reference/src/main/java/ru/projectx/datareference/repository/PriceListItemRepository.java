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

@Repository
public interface PriceListItemRepository extends JpaRepository<PriceListItem, UUID> {

    @Modifying(flushAutomatically = true)
    @Query("delete from PriceListItem item where item.service.id = :serviceId")
    int deleteByServiceId(@Param("serviceId") UUID serviceId);

    @Query("""
            select item from PriceListItem item
              join fetch item.service
             where item.service.id in :serviceIds
               and (item.validTo is null or item.validTo >= :today)
             order by item.validFrom
            """)
    List<PriceListItem> findCurrentByServiceIdIn(Collection<UUID> serviceIds, LocalDate today);

    @Query("""
            select item from PriceListItem item
             where item.service.id = :serviceId
               and item.validFrom < :checkOut
               and (item.validTo is null or item.validTo >= :checkIn)
             order by item.validFrom
            """)
    List<PriceListItem> findWithinPeriodByServiceId(UUID serviceId, LocalDate checkIn, LocalDate checkOut);
}

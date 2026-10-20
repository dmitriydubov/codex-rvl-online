package ru.projectx.datareference.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.catalog.AppealType;
import ru.projectx.datareference.entity.catalog.ContragentService;
import ru.projectx.datareference.entity.catalog.Lpu;
import ru.projectx.datareference.entity.catalog.LpuContract;
import ru.projectx.datareference.entity.catalog.PriceListItem;

public record LpuServicesResponse(NamedRef lpu, PriceContractRef priceContract, List<Service> services) {

    public static LpuServicesResponse of(
            Lpu lpu, LpuContract priceContract, PriceContractRef.Kind kind, List<Service> services) {

        return new LpuServicesResponse(NamedRef.of(lpu, lpu.name()), PriceContractRef.of(priceContract, kind), services);
    }

    public record Service(
            UUID id,
            UUID externalId,
            @Nullable String code,
            String name,
            NamedRef appealType,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable List<Price> prices) {

        public static Service withoutPrices(ContragentService service, AppealType appealType) {
            return new Service(service.id(), service.externalId(), service.code(), service.name(),
                    NamedRef.of(appealType, appealType.name()), null);
        }

        public static Service withPrices(ContragentService service, AppealType appealType, List<PriceListItem> prices) {
            return new Service(service.id(), service.externalId(), service.code(), service.name(),
                    NamedRef.of(appealType, appealType.name()), prices.stream().map(Price::of).toList());
        }
    }

    public record Price(
            BigDecimal price,
            @Nullable BigDecimal priceWithDiscount,
            @Nullable String serviceName,
            @Nullable String serviceCode,
            LocalDate validFrom,
            @Nullable LocalDate validTo) {

        static Price of(PriceListItem item) {
            return new Price(item.price(), item.priceWithDiscount(), item.serviceName(), item.serviceCode(),
                    item.validFrom(), item.validTo());
        }
    }
}

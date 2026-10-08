package ru.projectx.datareference.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.PriceListItem;

/**
 * Услуги, которые ЛПУ (лечебно-профилактическое учреждение) предлагает по прайсовому договору. Без запроса цен
 * поля {@code prices} нет вовсе, чтобы «цены не запрашивали» отличалось от «цен нет». Услуга без актуальных цен
 * остаётся с пустым массивом: расчёт её стоимости ответит {@code 422}, и видеть это надо заранее.
 */
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

        /** Цены не запрашивали: поля {@code prices} в ответе нет. */
        public static Service withoutPrices(ContragentService service, AppealType appealType) {
            return new Service(service.id(), service.externalId(), service.code(), service.name(),
                    NamedRef.of(appealType, appealType.name()), null);
        }

        /** Цены запрашивали: пустой перечень — актуальных цен у услуги нет. */
        public static Service withPrices(ContragentService service, AppealType appealType, List<PriceListItem> prices) {
            return new Service(service.id(), service.externalId(), service.code(), service.name(),
                    NamedRef.of(appealType, appealType.name()), prices.stream().map(Price::of).toList());
        }
    }

    /**
     * Строка прайса как есть, с обеими ценами. Какая из сумм основная для сооплаты, здесь не решается: это
     * отмечает расчёт стоимости услуги за период.
     */
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

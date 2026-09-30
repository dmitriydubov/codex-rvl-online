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
 * Услуги ЛПУ по прайсовому договору (R-03): позиции размещения по видам обращения РВЛ,
 * по наименованию, с ценами по запросу.
 *
 * <p>Без {@code need_prices} поля {@code prices} в услуге нет вовсе — не пустой массив,
 * а отсутствующее поле: иначе «цены не запрашивали» и «цен нет» выглядели бы одинаково.
 * С {@code need_prices} услуга без актуальных цен остаётся в списке с пустым массивом:
 * это диагностически важно — такая услуга потом даст {@code 422} в расчёте стоимости.
 */
public record LpuServicesResponse(LpuRef lpu, PriceContractRef priceContract, List<Service> services) {

    public static LpuServicesResponse of(
            Lpu lpu, LpuContract priceContract, PriceContractRef.Kind kind, List<Service> services) {

        return new LpuServicesResponse(LpuRef.of(lpu), PriceContractRef.of(priceContract, kind), services);
    }

    public record LpuRef(UUID id, UUID externalId, String name) {

        static LpuRef of(Lpu lpu) {
            return new LpuRef(lpu.id(), lpu.externalId(), lpu.name());
        }
    }

    public record Service(
            UUID id,
            UUID externalId,
            @Nullable String code,
            String name,
            AppealTypeRef appealType,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable List<Price> prices) {

        /** {@code prices == null} — цены не запрашивали; пустой список — запрашивали, но их нет. */
        public static Service of(ContragentService service, AppealType appealType, @Nullable List<PriceListItem> prices) {
            return new Service(service.id(), service.externalId(), service.code(), service.name(),
                    AppealTypeRef.of(appealType), prices == null ? null : prices.stream().map(Price::of).toList());
        }
    }

    public record AppealTypeRef(UUID id, UUID externalId, String name) {

        static AppealTypeRef of(AppealType appealType) {
            return new AppealTypeRef(appealType.id(), appealType.externalId(), appealType.name());
        }
    }

    /** Строка прайса как есть: основная цена по правилу В-6 выбирается в расчёте стоимости, не здесь. */
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

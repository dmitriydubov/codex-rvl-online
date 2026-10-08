package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Цена услуги санатория на период. Своего {@code external_id} у строки нет, поэтому она не наследует
 * {@link OneCEntity}: прейскурант замещается целиком по услуге. Пустая {@code validTo} означает открытую дату
 * (3000-01-01 в источнике), а не «неизвестно»: цена действует, пока её не заменят.
 */
@Entity
@Table(name = "price_list_items")
public class PriceListItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contragent_service_id", nullable = false)
    private ContragentService service;

    @Nullable
    @Column(name = "service_name")
    private String serviceName;

    @Nullable
    @Column(name = "service_code")
    private String serviceCode;

    @Nullable
    @Column(name = "service_code_pf")
    private String serviceCodePf;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Nullable
    @Column(name = "price_with_discount", precision = 15, scale = 2)
    private BigDecimal priceWithDiscount;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Nullable
    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PriceListItem() {
        // JPA
    }

    private PriceListItem(ContragentService service, Row row, Instant now) {
        this.id = UUID.randomUUID();
        this.service = service;
        this.serviceName = row.serviceName();
        this.serviceCode = row.serviceCode();
        this.serviceCodePf = row.serviceCodePf();
        this.price = row.price();
        this.priceWithDiscount = row.priceWithDiscount();
        this.validFrom = row.validFrom();
        this.validTo = row.validTo();
        this.createdAt = now;
    }

    public static PriceListItem of(ContragentService service, Row row, Instant now) {
        return new PriceListItem(service, row, now);
    }

    /**
     * Разобранная строка прейскуранта. Прейскурант по услуге приходит несколькими сообщениями со строками
     * и одним итоговым, а применяется, только когда все они собраны.
     */
    public record Row(
            @Nullable String serviceName,
            @Nullable String serviceCode,
            @Nullable String serviceCodePf,
            BigDecimal price,
            @Nullable BigDecimal priceWithDiscount,
            LocalDate validFrom,
            @Nullable LocalDate validTo) {
    }

    public UUID id() {
        return id;
    }

    public ContragentService service() {
        return service;
    }

    @Nullable
    public String serviceName() {
        return serviceName;
    }

    @Nullable
    public String serviceCode() {
        return serviceCode;
    }

    @Nullable
    public String serviceCodePf() {
        return serviceCodePf;
    }

    public BigDecimal price() {
        return price;
    }

    @Nullable
    public BigDecimal priceWithDiscount() {
        return priceWithDiscount;
    }

    public LocalDate validFrom() {
        return validFrom;
    }

    @Nullable
    public LocalDate validTo() {
        return validTo;
    }
}

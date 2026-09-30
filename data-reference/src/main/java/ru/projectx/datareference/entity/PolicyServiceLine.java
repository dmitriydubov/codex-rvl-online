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
 * Условия обслуживания по полису — строка второй табличной части потока ТЗ-1: слой
 * обслуживания, процент франшизы и период действия этих условий. Таблица источника
 * называется {@code policy_services}; в коде «сервисом» называется другое, поэтому
 * класс назван строкой.
 *
 * <p>{@link #copayment} — вторая половина того же расхождения контракта, что и лимит
 * в {@link PolicyProgram}: здесь сооплата задана процентом, а требования ЛК описывают
 * превышение лимита. Поле сохраняется как есть и в расчёт не идёт до ответа по открытому вопросу.
 *
 * <p>Ссылка на программу здесь <b>необязательна</b>, в отличие от {@link PolicyProgram}:
 * маппинг ТЗ-1 помечает её необязательной, а нулевой GUID означает «ссылки нет».
 */
@Entity
@Table(name = "policy_services")
public class PolicyServiceLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false, updatable = false)
    private Policy policy;

    @Nullable
    @Column(name = "external_program_id")
    private UUID externalProgramId;

    @Column(name = "layer_number", nullable = false)
    private int layerNumber;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal copayment;

    @Nullable
    @Column(name = "begin_date")
    private LocalDate beginDate;

    @Nullable
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean availability;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PolicyServiceLine() {
        // JPA
    }

    private PolicyServiceLine(Policy policy, Row row, Instant now) {
        this.id = UUID.randomUUID();
        this.policy = policy;
        this.externalProgramId = row.externalProgramId();
        this.layerNumber = row.layerNumber();
        this.copayment = row.copayment();
        this.beginDate = row.beginDate();
        this.endDate = row.endDate();
        this.availability = row.availability();
        this.createdAt = now;
    }

    static PolicyServiceLine of(Policy policy, Row row, Instant now) {
        return new PolicyServiceLine(policy, row, now);
    }

    /** Разобранная строка табличной части условий обслуживания. */
    public record Row(
            @Nullable UUID externalProgramId,
            int layerNumber,
            BigDecimal copayment,
            @Nullable LocalDate beginDate,
            @Nullable LocalDate endDate,
            boolean availability) {
    }

    public UUID id() {
        return id;
    }

    @Nullable
    public UUID externalProgramId() {
        return externalProgramId;
    }

    public int layerNumber() {
        return layerNumber;
    }

    public BigDecimal copayment() {
        return copayment;
    }

    @Nullable
    public LocalDate beginDate() {
        return beginDate;
    }

    @Nullable
    public LocalDate endDate() {
        return endDate;
    }

    public boolean availability() {
        return availability;
    }
}

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
 * Программа, подключённая застрахованному по полису (поток полисов, ТЗ-1). Лимит хранится как есть, а доплату
 * сервис не считает: контракт описывает сразу процент франшизы и превышение лимита и не объясняет, как они сочетаются.
 * Программа указана GUID'ом без ссылки, потому что программы не РВЛ в справочнике нет намеренно.
 */
@Entity
@Table(name = "policy_programs")
public class PolicyProgram {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false, updatable = false)
    private Policy policy;

    @Column(name = "external_program_id", nullable = false)
    private UUID externalProgramId;

    @Column(name = "begin_date", nullable = false)
    private LocalDate beginDate;

    @Nullable
    @Column(name = "end_date")
    private LocalDate endDate;

    @Nullable
    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Nullable
    @Column(name = "amount_d", precision = 15, scale = 2)
    private BigDecimal amountD;

    @Nullable
    @Column(precision = 15, scale = 2)
    private BigDecimal premium;

    @Nullable
    @Column(name = "premium_d", precision = 15, scale = 2)
    private BigDecimal premiumD;

    @Column(name = "limit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal limitAmount;

    @Column(nullable = false)
    private boolean availability;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PolicyProgram() {
        // JPA
    }

    private PolicyProgram(Policy policy, Row row, Instant now) {
        this.id = UUID.randomUUID();
        this.policy = policy;
        this.externalProgramId = row.externalProgramId();
        this.beginDate = row.beginDate();
        this.endDate = row.endDate();
        this.amount = row.amount();
        this.amountD = row.amountD();
        this.premium = row.premium();
        this.premiumD = row.premiumD();
        this.limitAmount = row.limitAmount();
        this.availability = row.availability();
        this.createdAt = now;
    }

    static PolicyProgram of(Policy policy, Row row, Instant now) {
        return new PolicyProgram(policy, row, now);
    }

    /** Разобранная строка табличной части программ. */
    public record Row(
            UUID externalProgramId,
            LocalDate beginDate,
            @Nullable LocalDate endDate,
            @Nullable BigDecimal amount,
            @Nullable BigDecimal amountD,
            @Nullable BigDecimal premium,
            @Nullable BigDecimal premiumD,
            BigDecimal limitAmount,
            boolean availability) {
    }

    public UUID id() {
        return id;
    }

    public UUID externalProgramId() {
        return externalProgramId;
    }

    public LocalDate beginDate() {
        return beginDate;
    }

    @Nullable
    public LocalDate endDate() {
        return endDate;
    }

    @Nullable
    public BigDecimal amount() {
        return amount;
    }

    @Nullable
    public BigDecimal amountD() {
        return amountD;
    }

    @Nullable
    public BigDecimal premium() {
        return premium;
    }

    @Nullable
    public BigDecimal premiumD() {
        return premiumD;
    }

    public BigDecimal limitAmount() {
        return limitAmount;
    }

    public boolean availability() {
        return availability;
    }
}

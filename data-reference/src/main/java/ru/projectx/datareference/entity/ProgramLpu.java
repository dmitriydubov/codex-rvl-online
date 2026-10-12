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

@Entity
@Table(name = "program_lpu")
public class ProgramLpu {

    public enum CoverageType {
        R, D
    }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lpu_id", nullable = false)
    private Lpu lpu;

    @Nullable
    @Column(name = "external_appeal_type_id")
    private UUID externalAppealTypeId;

    @Nullable
    @Column(name = "begin_date")
    private LocalDate beginDate;

    @Nullable
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "layer_number", nullable = false)
    private int layerNumber;

    @Column(name = "direct_attachment", nullable = false)
    private boolean directAttachment;

    @Column(name = "percent_copayment", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentCopayment;

    @Column(name = "type_rd", nullable = false, length = 1)
    private String typeRd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProgramLpu() {
    }

    private ProgramLpu(Program program, Row row, Instant now) {
        this.id = UUID.randomUUID();
        this.program = program;
        this.lpu = row.lpu();
        this.externalAppealTypeId = row.externalAppealTypeId();
        this.beginDate = row.beginDate();
        this.endDate = row.endDate();
        this.layerNumber = row.layerNumber();
        this.directAttachment = row.directAttachment();
        this.percentCopayment = row.percentCopayment();
        this.typeRd = row.coverageType().name();
        this.createdAt = now;
    }

    static ProgramLpu of(Program program, Row row, Instant now) {
        return new ProgramLpu(program, row, now);
    }

    public record Row(
            Lpu lpu,
            @Nullable UUID externalAppealTypeId,
            @Nullable LocalDate beginDate,
            @Nullable LocalDate endDate,
            int layerNumber,
            boolean directAttachment,
            BigDecimal percentCopayment,
            CoverageType coverageType) {
    }

    public Program program() {
        return program;
    }

    public Lpu lpu() {
        return lpu;
    }

    @Nullable
    public UUID externalAppealTypeId() {
        return externalAppealTypeId;
    }

    @Nullable
    public LocalDate beginDate() {
        return beginDate;
    }

    @Nullable
    public LocalDate endDate() {
        return endDate;
    }

    public int layerNumber() {
        return layerNumber;
    }

    public boolean directAttachment() {
        return directAttachment;
    }

    public BigDecimal percentCopayment() {
        return percentCopayment;
    }

    public CoverageType coverageType() {
        return CoverageType.valueOf(typeRd);
    }
}

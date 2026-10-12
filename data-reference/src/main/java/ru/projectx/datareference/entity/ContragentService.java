package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "contragent_services")
public class ContragentService extends OneCEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lpu_contract_id", nullable = false)
    private LpuContract contract;

    @Nullable
    @Column(name = "external_appeal_type_id")
    private UUID externalAppealTypeId;

    @Nullable
    private String code;

    @Column(nullable = false)
    private String name;

    protected ContragentService() {
    }

    private ContragentService(UUID externalId, Instant now) {
        super(externalId, now);
    }

    public static ContragentService create(UUID externalId, LpuContract contract, Details details, Instant now) {
        ContragentService service = new ContragentService(externalId, now);
        service.applySnapshot(contract, details, now);
        return service;
    }

    public void applySnapshot(LpuContract contract, Details details, Instant now) {
        this.contract = contract;
        this.externalAppealTypeId = details.externalAppealTypeId();
        this.code = details.code();
        this.name = details.name();
        applyCommon(details.deleteMark(), now);
    }

    public record Details(
            @Nullable UUID externalAppealTypeId,
            @Nullable String code,
            String name,
            boolean deleteMark) {
    }

    public LpuContract contract() {
        return contract;
    }

    @Nullable
    public UUID externalAppealTypeId() {
        return externalAppealTypeId;
    }

    @Nullable
    public String code() {
        return code;
    }

    public String name() {
        return name;
    }
}

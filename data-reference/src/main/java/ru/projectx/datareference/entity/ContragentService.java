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

/**
 * Услуга, которую санаторий оказывает по договору. К услуге привязан прейскурант, поэтому цены
 * замещаются целиком по услуге.
 *
 * <p>Номенклатура медицинских услуг отдельным справочником не хранится. Вид обращения хранится
 * GUID'ом источника: в сообщении он необязателен, и услуга не должна пропадать из-за справочника,
 * который ещё не приехал.
 */
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
        // JPA
    }

    private ContragentService(UUID externalId, Instant now) {
        super(externalId, now);
    }

    /**
     * Новая запись из первого сообщения. Поля заполняет фабрика, а не конструктор: метод, который
     * наследник может переопределить, из конструктора вызывать нельзя — наследник получил бы
     * вызов раньше, чем инициализировались его собственные поля.
     */
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

    /** Разобранные поля сообщения об услуге санатория. */
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

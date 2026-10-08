package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Договор с санаторием, к которому привязаны услуги и их цены. Признак основного договора не хранится, а выводится
 * при расчёте цены из полей источника, чтобы смена правила не оставляла в базе устаревших значений. В архив договор
 * уходит только по пометке удаления в 1С, иначе он молча разошёлся бы с источником.
 */
@Entity
@Table(name = "lpu_contracts")
public class LpuContract extends OneCEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lpu_id", nullable = false)
    private Lpu lpu;

    @Nullable
    private String number;

    @Nullable
    private String name;

    /**
     * GUID головного договора, от которого пролонгирован этот; пусто, если в источнике нулевой GUID. Ссылки нет,
     * потому что головной договор может не прийти или принадлежать чужому контрагенту.
     */
    @Nullable
    @Column(name = "main_contract_external_id")
    private UUID mainContractExternalId;

    /** Брать цены головного договора, а не собственные ({@code usePricesMainAgreement} в 1С). */
    @Column(name = "use_prices_main_contract", nullable = false)
    private boolean usePricesMainContract;

    /** Выгружаются ли прейскуранты договора в систему онлайн-бронирования; с ней работает сама 1С. */
    @Column(name = "uploaded_to_sob", nullable = false)
    private boolean uploadedToSob;

    @Column(name = "begin_date", nullable = false)
    private LocalDate beginDate;

    /** {@code null} — открытая дата: в источнике это 3000-01-01, а не «через тысячу лет». */
    @Nullable
    @Column(name = "end_date")
    private LocalDate endDate;

    protected LpuContract() {
        // JPA
    }

    private LpuContract(UUID externalId, Instant now) {
        super(externalId, now);
    }

    /** Новая запись из первого сообщения 1С. */
    public static LpuContract create(UUID externalId, Lpu lpu, Details details, Instant now) {
        LpuContract contract = new LpuContract(externalId, now);
        contract.applySnapshot(lpu, details, now);
        return contract;
    }

    /**
     * Санаторий передаётся и при обновлении, потому что привязка договора может смениться: договор отделения
     * принадлежит отделению, а отделение у нас отдельный санаторий.
     */
    public void applySnapshot(Lpu lpu, Details details, Instant now) {
        this.lpu = lpu;
        this.number = details.number();
        this.name = details.name();
        this.mainContractExternalId = details.mainContractExternalId();
        this.usePricesMainContract = details.usePricesMainContract();
        this.uploadedToSob = details.uploadedToSob();
        this.beginDate = details.beginDate();
        this.endDate = details.endDate();
        applyCommon(details.deleteMark(), now);
    }

    /** Разобранные поля сообщения о договоре с санаторием. */
    public record Details(
            @Nullable String number,
            @Nullable String name,
            @Nullable UUID mainContractExternalId,
            boolean usePricesMainContract,
            boolean uploadedToSob,
            LocalDate beginDate,
            @Nullable LocalDate endDate,
            boolean deleteMark) {
    }

    public Lpu lpu() {
        return lpu;
    }

    @Nullable
    public String number() {
        return number;
    }

    @Nullable
    public String name() {
        return name;
    }

    @Nullable
    public UUID mainContractExternalId() {
        return mainContractExternalId;
    }

    public boolean usePricesMainContract() {
        return usePricesMainContract;
    }

    public boolean uploadedToSob() {
        return uploadedToSob;
    }

    public LocalDate beginDate() {
        return beginDate;
    }

    @Nullable
    public LocalDate endDate() {
        return endDate;
    }
}

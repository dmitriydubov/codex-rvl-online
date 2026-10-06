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
 * Договор с санаторием на оказание услуг. К договору привязаны услуги, а к ним — цены,
 * поэтому он корень всей ценовой части каталога. Хранится только актуальный срок действия,
 * без истории периодов.
 *
 * <p>Признак «основной договор» не хранится: вместо него лежат два поля источника, из которых
 * он выводится, — {@link #mainContractExternalId} и {@link #usePricesMainContract}. Вывод
 * делается при расчёте цены, поэтому смена правила не оставляет устаревших значений в базе.
 *
 * <p>Архивным договор становится только по пометке удаления в 1С. Пометка по нашей инициативе
 * молча разошлась бы с источником, и исправить её было бы некому.
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
     * Внешний GUID головного договора, от которого пролонгирован текущий. {@code null} —
     * договор сам по себе: в источнике это нулевой GUID, а не отсутствие поля.
     *
     * <p>Хранится GUID источника, а не ссылка на нашу запись: головной договор мог
     * не приехать или оказаться договором чужого контрагента, и внешний ключ отверг бы
     * обычную строку договора.
     */
    @Nullable
    @Column(name = "main_contract_external_id")
    private UUID mainContractExternalId;

    /** Брать цены головного договора, а не собственные ({@code usePricesMainAgreement} в 1С). */
    @Column(name = "use_prices_main_contract", nullable = false)
    private boolean usePricesMainContract;

    /**
     * Выгружаются ли прейскуранты договора в систему онлайн-бронирования (СОБ).
     *
     * <p>С самой СОБ разговаривает 1С, а сюда её признак приходит данными. Бронирование
     * доступно только по подключённым договорам.
     */
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

    /**
     * Новая запись из первого сообщения. Поля заполняет фабрика, а не конструктор: метод, который
     * наследник может переопределить, из конструктора вызывать нельзя — наследник получил бы
     * вызов раньше, чем инициализировались его собственные поля.
     */
    public static LpuContract create(UUID externalId, Lpu lpu, Details details, Instant now) {
        LpuContract contract = new LpuContract(externalId, now);
        contract.applySnapshot(lpu, details, now);
        return contract;
    }

    /**
     * Санаторий передаётся и при обновлении: договор приходит с тем же {@code external_id},
     * но привязка может смениться. Договор отделения принадлежит отделению, а отделение
     * у нас — отдельный санаторий.
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

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
 * Договор с санаторием на оказание услуг (ТЗ-5). К договору привязаны услуги (ТЗ-10),
 * а к ним — цены (ТЗ-9), поэтому он корень всей ценовой части каталога.
 *
 * <p>История периодов действия не хранится: ТЗ-5 прямо оговаривает, что нужен только
 * актуальный срок.
 *
 * <p><b>Признака «основной договор» здесь больше нет.</b> До ТЗ-5 v2 запись хранила
 * вычисленный {@code main_price_dog}; теперь хранятся два поля источника, из которых
 * он выводился, — {@link #mainContractExternalId} и {@link #usePricesMainContract}, —
 * а сам вывод делается при расчёте цены. Так честнее: сохранённое вычисление устаревает
 * молча, когда меняется правило, а исходные данные не устаревают никогда.
 *
 * <p>Перехода, который архивировал бы договор по нашей инициативе, здесь нет намеренно:
 * {@code is_archive} приходит из {@code deleteMark} источника и только оттуда. Запись,
 * которую мы пометили сами, расходится с 1С молча — там договор действующий, у нас
 * архивный, и сообщения, способного это исправить, может не быть годами.
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
     * не приехать или оказаться договором чужого контрагента, и жёсткий FK превратил бы
     * это в отказ применения там, где ТЗ-5 ждёт обычной строки.
     */
    @Nullable
    @Column(name = "main_contract_external_id")
    private UUID mainContractExternalId;

    /** Брать цены головного договора, а не собственные (ТЗ-5 v2, {@code usePricesMainAgreement}). */
    @Column(name = "use_prices_main_contract", nullable = false)
    private boolean usePricesMainContract;

    /**
     * «Выгружать прейскуранты в систему бронирования» — участвует ли договор в СОБ.
     *
     * <p>С самой СОБ разговаривает 1С, а не мы; сюда её признак приходит <b>данными</b>,
     * как и {@code booking_source} в ответах по заявкам. Признак назван критичным
     * для бизнес-процесса: бронирование доступно только по подключённым договорам.
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

    private LpuContract(UUID externalId, Lpu lpu, Details details, Instant now) {
        super(externalId, now);
        applySnapshot(lpu, details, now);
    }

    public static LpuContract create(UUID externalId, Lpu lpu, Details details, Instant now) {
        return new LpuContract(externalId, lpu, details, now);
    }

    /**
     * Санаторий передаётся и при обновлении: договор приходит с тем же
     * {@code external_id}, но привязка может смениться — договор отделения принадлежит
     * отделению, а отделение у нас отдельный санаторий (УСЛОВИЕ 2 ТЗ-5).
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

    /** Разобранные поля сообщения ТЗ-5. */
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

package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Договор добровольного медицинского страхования (ДМС) из 1С. Договоры других продуктов, например
 * еОСАГО, отбрасываются при разборе сообщения.
 *
 * <p>Полис ссылается на договор GUID'ом без внешнего ключа: полисы приезжают своим потоком, бывает,
 * что раньше договора, и отсутствие договора ошибкой не считается.
 *
 * <p>Страхователь связан так же: им может быть физическое лицо, а в справочник страхователей попадают
 * только юридические лица. Внешний ключ превратил бы такой договор в отказ применения.
 */
@Entity
@Table(name = "insurance_contracts")
public class InsuranceContract extends OneCEntity {

    @Column(nullable = false)
    private String number;

    @Column(name = "begin_date", nullable = false)
    private LocalDate beginDate;

    /** {@code null} — открытая дата: в источнике это 3000-01-01, а не «через тысячу лет». */
    @Nullable
    @Column(name = "end_date")
    private LocalDate endDate;

    /** GUID страхователя в 1С, без внешнего ключа. */
    @Nullable
    @Column(name = "external_insurer_id")
    private UUID externalInsurerId;

    protected InsuranceContract() {
        // JPA
    }

    private InsuranceContract(UUID externalId, Instant now) {
        super(externalId, now);
    }

    /**
     * Новая запись из первого сообщения. Поля заполняет фабрика, а не конструктор: метод, который
     * наследник может переопределить, из конструктора вызывать нельзя — наследник получил бы
     * вызов раньше, чем инициализировались его собственные поля.
     */
    public static InsuranceContract create(UUID externalId, Details details, Instant now) {
        InsuranceContract contract = new InsuranceContract(externalId, now);
        contract.applySnapshot(details, now);
        return contract;
    }

    public void applySnapshot(Details details, Instant now) {
        this.number = details.number();
        this.beginDate = details.beginDate();
        this.endDate = details.endDate();
        this.externalInsurerId = details.externalInsurerId();
        applyCommon(details.notActive(), now);
    }

    /**
     * Разобранные поля сообщения о договоре страхования.
     *
     * @param notActive признак архивности этого потока. Называется не {@code deleteMark},
     *                как у остальных справочников, а {@code notActive} — имя источника
     *                сохранено, чтобы поле в записи и поле в сообщении читались как одно
     */
    public record Details(
            String number,
            LocalDate beginDate,
            @Nullable LocalDate endDate,
            @Nullable UUID externalInsurerId,
            boolean notActive) {
    }

    public String number() {
        return number;
    }

    public LocalDate beginDate() {
        return beginDate;
    }

    @Nullable
    public LocalDate endDate() {
        return endDate;
    }

    @Nullable
    public UUID externalInsurerId() {
        return externalInsurerId;
    }
}

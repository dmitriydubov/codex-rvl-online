package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Договор страхования (ТЗ-2). Хранятся только договоры ДМС — прочие продукты источника
 * (еОСАГО и остальные) фильтруются при разборе и до этой таблицы не доходят.
 *
 * <p>Полис ссылается на договор полем {@code external_contract_id}, и ссылка эта
 * логическая: полисы приезжают своим потоком и раньше договоров, а ТЗ-1 прямо
 * оговаривает, что отсутствие связанной записи ошибкой не считается.
 *
 * <p>Страхователь тоже связан логически, и причина названа в самом ТЗ-2: страхователем
 * может быть физлицо, которого в {@code insurers} нет вовсе — туда попадают только
 * контрагенты-юрлица из ТЗ-3. Внешний ключ превратил бы такой договор в отказ применения.
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

    /** GUID страхователя в 1С. Без FK — см. javadoc класса. */
    @Nullable
    @Column(name = "external_insurer_id")
    private UUID externalInsurerId;

    protected InsuranceContract() {
        // JPA
    }

    private InsuranceContract(UUID externalId, Details details, Instant now) {
        super(externalId, now);
        applySnapshot(details, now);
    }

    public static InsuranceContract create(UUID externalId, Details details, Instant now) {
        return new InsuranceContract(externalId, details, now);
    }

    public void applySnapshot(Details details, Instant now) {
        this.number = details.number();
        this.beginDate = details.beginDate();
        this.endDate = details.endDate();
        this.externalInsurerId = details.externalInsurerId();
        applyCommon(details.notActive(), now);
    }

    /**
     * Разобранные поля сообщения ТЗ-2.
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

package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Договор добровольного медицинского страхования из 1С. Полис и страхователь связаны с ним GUID'ом без внешнего
 * ключа: полис может прийти раньше договора, а страхователь-человек в справочник страхователей не попадает.
 * Договор действует с первого по последний день срока включительно, пока он не в архиве.
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

    @Nullable
    @Column(name = "external_insurer_id")
    private UUID externalInsurerId;

    protected InsuranceContract() {
        // JPA
    }

    private InsuranceContract(UUID externalId, Instant now) {
        super(externalId, now);
    }

    /** Новая запись из первого сообщения 1С. */
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

    /** {@code notActive} — признак архивности; имя взято из сообщения 1С, а не {@code deleteMark}, как у остальных. */
    public record Details(
            String number,
            LocalDate beginDate,
            @Nullable LocalDate endDate,
            @Nullable UUID externalInsurerId,
            boolean notActive) {
    }

    public boolean isActiveOn(LocalDate day) {
        return !isArchive() && covers(day);
    }

    private boolean covers(LocalDate day) {
        return !beginDate.isAfter(day) && (endDate == null || !endDate.isBefore(day));
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

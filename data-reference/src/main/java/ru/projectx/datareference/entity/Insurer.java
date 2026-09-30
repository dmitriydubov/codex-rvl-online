package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Страхователь — юрлицо, заключившее договор страхования в пользу своих сотрудников
 * (ТЗ-3, ветка {@code thisIsLPU = false}). Фильтра у ветки нет: сохраняются все.
 *
 * <p>Справочник приезжает <b>одним потоком с санаториями</b>, поэтому и разбирается
 * рядом с ними, хотя смысл у него другой. Именно к страхователю привязан HR: его ручки
 * отвечают за сотрудников своего юрлица.
 */
@Entity
@Table(name = "insurers")
public class Insurer extends OneCEntity {

    @Column(nullable = false)
    private String name;

    @Nullable
    @Column(name = "full_name")
    private String fullName;

    @Nullable
    @Column(length = 12)
    private String inn;

    @Nullable
    @Column(length = 9)
    private String kpp;

    protected Insurer() {
        // JPA
    }

    private Insurer(UUID externalId, Details details, Instant now) {
        super(externalId, now);
        applySnapshot(details, now);
    }

    public static Insurer create(UUID externalId, Details details, Instant now) {
        return new Insurer(externalId, details, now);
    }

    public void applySnapshot(Details details, Instant now) {
        this.name = details.name();
        this.fullName = details.fullName();
        this.inn = details.inn();
        this.kpp = details.kpp();
        applyCommon(details.deleteMark(), now);
    }

    /** Разобранные поля сообщения ТЗ-3, ветка страхователя. */
    public record Details(
            String name,
            @Nullable String fullName,
            @Nullable String inn,
            @Nullable String kpp,
            boolean deleteMark) {
    }

    public String name() {
        return name;
    }

    @Nullable
    public String fullName() {
        return fullName;
    }

    @Nullable
    public String inn() {
        return inn;
    }

    @Nullable
    public String kpp() {
        return kpp;
    }
}

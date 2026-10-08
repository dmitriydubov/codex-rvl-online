package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Страхователь, то есть юридическое лицо, застраховавшее своих сотрудников. Приходит одним потоком с санаториями
 * как контрагент без отметки лечебного учреждения, и сохраняются все такие контрагенты без отбора.
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

    private Insurer(UUID externalId, Instant now) {
        super(externalId, now);
    }

    /** Новая запись из первого сообщения 1С. */
    public static Insurer create(UUID externalId, Details details, Instant now) {
        Insurer insurer = new Insurer(externalId, now);
        insurer.applySnapshot(details, now);
        return insurer;
    }

    public void applySnapshot(Details details, Instant now) {
        this.name = details.name();
        this.fullName = details.fullName();
        this.inn = details.inn();
        this.kpp = details.kpp();
        applyCommon(details.deleteMark(), now);
    }

    /** Разобранные поля сообщения о контрагенте-страхователе. */
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

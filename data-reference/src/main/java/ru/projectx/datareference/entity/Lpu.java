package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Санаторий (лечебно-профилактическое учреждение, ЛПУ) — карточка объекта каталога.
 * Сохраняются только контрагенты 1С с видом деятельности РВЛ: этим признаком санатории
 * отделяются от прочих юрлиц.
 *
 * <p>У записи два источника: реквизиты приходят с контрагентом, а адрес и регион —
 * <b>отдельным</b> сообщением о контактах, своим переходом {@link #applyContacts}.
 * Сообщение о контрагенте адрес не трогает, и наоборот.
 */
@Entity
@Table(name = "lpu")
public class Lpu extends OneCEntity {

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

    @Nullable
    private String phone;

    @Nullable
    private String email;

    /** Приходит сообщением о контактах, а не с контрагентом. */
    @Nullable
    private String region;

    /** Приходит сообщением о контактах, а не с контрагентом. */
    @Nullable
    private String address;

    protected Lpu() {
        // JPA
    }

    private Lpu(UUID externalId, Instant now) {
        super(externalId, now);
    }

    /** Новая запись из первого сообщения 1С. */
    public static Lpu create(UUID externalId, Details details, Instant now) {
        Lpu lpu = new Lpu(externalId, now);
        lpu.applySnapshot(details, now);
        return lpu;
    }

    public void applySnapshot(Details details, Instant now) {
        this.name = details.name();
        this.fullName = details.fullName();
        this.inn = details.inn();
        this.kpp = details.kpp();
        this.phone = details.phone();
        this.email = details.email();
        applyCommon(details.deleteMark(), now);
    }

    /**
     * Замещает адрес и регион из сообщения о контактах. Пустое значение очищает поле:
     * оно означает, что в 1С адреса больше нет, а не что сообщение неполное.
     *
     * <p>Признак архивности не меняется: сообщение о контактах о нём не знает, и сбросить
     * его здесь значило бы вернуть из архива санаторий чужим сообщением.
     */
    public void applyContacts(@Nullable String address, @Nullable String region, Instant now) {
        this.address = address;
        this.region = region;
        applyCommon(isArchive(), now);
    }

    /**
     * В новом сообщении пропал вид деятельности РВЛ. Запись не удаляется, а уходит в архив;
     * реквизиты при этом всё равно обновляются, потому что сообщение принесло актуальные.
     */
    public void archiveAfterLosingRvl(Details details, Instant now) {
        applySnapshot(details, now);
        archiveCommon(now);
    }

    /** Разобранные поля сообщения о контрагенте-санатории. */
    public record Details(
            String name,
            @Nullable String fullName,
            @Nullable String inn,
            @Nullable String kpp,
            @Nullable String phone,
            @Nullable String email,
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

    @Nullable
    public String phone() {
        return phone;
    }

    @Nullable
    public String email() {
        return email;
    }

    @Nullable
    public String region() {
        return region;
    }

    @Nullable
    public String address() {
        return address;
    }
}

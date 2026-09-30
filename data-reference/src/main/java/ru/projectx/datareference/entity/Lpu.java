package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Санаторий — карточка объекта каталога (ТЗ-3, ветка {@code thisIsLPU = true}).
 * Сохраняются только контрагенты с видом деятельности РВЛ: этим признаком санатории
 * отделяются от прочих юрлиц 1С:УСК.
 *
 * <p><b>Три источника у одной записи, и путать их нельзя:</b> реквизиты приходят
 * потоком ТЗ-3, а адрес и регион — <b>отдельным</b> потоком ТЗ-8, своим переходом
 * {@link #applyContacts}. Сообщение ТЗ-3 адрес не трогает, и наоборот.
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

    /** Приходит потоком ТЗ-8, а не ТЗ-3. */
    @Nullable
    private String region;

    /** Приходит потоком ТЗ-8, а не ТЗ-3. */
    @Nullable
    private String address;

    protected Lpu() {
        // JPA
    }

    private Lpu(UUID externalId, Details details, Instant now) {
        super(externalId, now);
        applySnapshot(details, now);
    }

    public static Lpu create(UUID externalId, Details details, Instant now) {
        return new Lpu(externalId, details, now);
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
     * ТЗ-8: адрес и регион ведутся отдельным потоком и в режиме замещения — если
     * подходящих строк-адресов в сообщении нет, значения <b>очищаются</b>. Пустой адрес
     * здесь означает «в 1С его больше нет», а не «сообщение неполное»: оставить прежние
     * значения значило бы показывать адрес, которого в источнике уже нет, и никогда
     * об этом не узнать.
     *
     * <p>Признак архивности переносится в себя же: сообщение ТЗ-8 несёт только адрес
     * и о {@code deleteMark} записи не знает, а обнулить его здесь значило бы воскресить
     * архивный санаторий чужим потоком.
     */
    public void applyContacts(@Nullable String address, @Nullable String region, Instant now) {
        this.address = address;
        this.region = region;
        applyCommon(isArchive(), now);
    }

    /**
     * ТЗ-3, п. 3.3: в новом сообщении пропал вид деятельности РВЛ. Запись не удаляется,
     * а помечается архивной — реквизиты при этом всё равно обновляются, потому что
     * сообщение принесло актуальные.
     */
    public void archiveAfterLosingRvl(Details details, Instant now) {
        applySnapshot(details, now);
        archiveCommon(now);
    }

    /** Разобранные поля сообщения ТЗ-3. */
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

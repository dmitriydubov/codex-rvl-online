package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Страхователь — юридическое лицо, заключившее договор страхования в пользу своих сотрудников.
 *
 * <p>Страхователи приезжают одним потоком с санаториями: это контрагенты, не отмеченные как
 * лечебное учреждение. Отбора у них нет, сохраняются все. К страхователю привязан сервис Заявок:
 * его запросы касаются сотрудников своего юридического лица.
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

    /**
     * Новая запись из первого сообщения. Поля заполняет фабрика, а не конструктор: метод, который
     * наследник может переопределить, из конструктора вызывать нельзя — наследник получил бы
     * вызов раньше, чем инициализировались его собственные поля.
     */
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

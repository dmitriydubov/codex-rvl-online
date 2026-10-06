package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Вид обращения за медицинской помощью из 1С: амбулаторная помощь, стационар,
 * восстановительное лечение и так далее. Справочник иерархический и сохраняется целиком.
 *
 * <p>Родитель хранится GUID'ом источника по той же причине, что и у {@link Mkb10}.
 */
@Entity
@Table(name = "appeal_types")
public class AppealType extends OneCEntity {

    @Nullable
    @Column(name = "external_parent_id")
    private UUID externalParentId;

    @Nullable
    @Column(name = "external_code")
    private String externalCode;

    @Column(nullable = false)
    private String name;

    @Nullable
    private String category;

    @Nullable
    @Column(name = "system_code")
    private String systemCode;

    @Nullable
    @Column(name = "hierarchy_level")
    private Integer hierarchyLevel;

    /** Прикрепление напрямую к учреждению. */
    @Column(name = "direct_attachment", nullable = false)
    private boolean directAttachment;

    /** Прикрепление не напрямую — через направление. Флаги независимы. */
    @Column(name = "non_direct_attachment", nullable = false)
    private boolean nonDirectAttachment;

    protected AppealType() {
        // JPA
    }

    private AppealType(UUID externalId, Instant now) {
        super(externalId, now);
    }

    /**
     * Новая запись из первого сообщения. Поля заполняет фабрика, а не конструктор: метод, который
     * наследник может переопределить, из конструктора вызывать нельзя — наследник получил бы
     * вызов раньше, чем инициализировались его собственные поля.
     */
    public static AppealType create(UUID externalId, Details details, Instant now) {
        AppealType appealType = new AppealType(externalId, now);
        appealType.applySnapshot(details, now);
        return appealType;
    }

    public void applySnapshot(Details details, Instant now) {
        this.externalParentId = details.externalParentId();
        this.externalCode = details.externalCode();
        this.name = details.name();
        this.category = details.category();
        this.systemCode = details.systemCode();
        this.hierarchyLevel = details.hierarchyLevel();
        this.directAttachment = details.directAttachment();
        this.nonDirectAttachment = details.nonDirectAttachment();
        applyCommon(details.deleteMark(), now);
    }

    /** Разобранные поля сообщения о виде обращения. */
    public record Details(
            @Nullable UUID externalParentId,
            @Nullable String externalCode,
            String name,
            @Nullable String category,
            @Nullable String systemCode,
            @Nullable Integer hierarchyLevel,
            boolean directAttachment,
            boolean nonDirectAttachment,
            boolean deleteMark) {
    }

    @Nullable
    public UUID externalParentId() {
        return externalParentId;
    }

    @Nullable
    public String externalCode() {
        return externalCode;
    }

    public String name() {
        return name;
    }

    @Nullable
    public String category() {
        return category;
    }

    @Nullable
    public String systemCode() {
        return systemCode;
    }

    @Nullable
    public Integer hierarchyLevel() {
        return hierarchyLevel;
    }

    public boolean directAttachment() {
        return directAttachment;
    }

    public boolean nonDirectAttachment() {
        return nonDirectAttachment;
    }
}

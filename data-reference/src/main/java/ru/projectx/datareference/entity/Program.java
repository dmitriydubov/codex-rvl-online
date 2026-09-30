package ru.projectx.datareference.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Страховая программа РВЛ (ТЗ-6) вместе с перечнем доступных по ней учреждений.
 *
 * <p>Перечень — табличная часть сообщения и живёт по правилу <b>полного замещения</b>:
 * строки принадлежат программе, а не существуют сами по себе, отсюда
 * {@code orphanRemoval} и замена целиком. Дописывать в него по одной строке нельзя —
 * источник присылает актуальный состав, и «дописать» означало бы навсегда сохранить
 * строки, которых в 1С уже нет.
 */
@Entity
@Table(name = "programs")
public class Program extends OneCEntity {

    @Nullable
    @Column(name = "external_product_id")
    private UUID externalProductId;

    @Nullable
    @Column(name = "external_parent_id")
    private UUID externalParentId;

    @Column(nullable = false)
    private String name;

    @Nullable
    @Column(name = "type_programm")
    private String typeProgramm;

    /*
     * Поле не final, хотя по стилю проекта хотелось бы: Hibernate подменяет коллекцию
     * своей реализацией через reflection, и final здесь — известная мина, которая
     * срабатывает не на компиляции, а на первой загрузке сущности.
     */
    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramLpu> availableLpu = new ArrayList<>();

    protected Program() {
        // JPA
    }

    private Program(UUID externalId, Details details, Instant now) {
        super(externalId, now);
        applySnapshot(details, now);
    }

    public static Program create(UUID externalId, Details details, Instant now) {
        return new Program(externalId, details, now);
    }

    public void applySnapshot(Details details, Instant now) {
        this.externalProductId = details.externalProductId();
        this.externalParentId = details.externalParentId();
        this.name = details.name();
        this.typeProgramm = details.typeProgramm();
        applyCommon(details.deleteMark(), now);
    }

    /** ТЗ-6, п. 6.3: пришло обновление с {@code rvl = false} — программа больше не наша. */
    public void archiveAfterLosingRvl(Details details, Instant now) {
        applySnapshot(details, now);
        archiveCommon(now);
    }

    /**
     * Полное замещение табличной части {@code service[]}.
     *
     * <p>Обычный цикл, а не стрим: он наполняет коллекцию сущности, то есть имеет
     * побочный эффект по определению.
     */
    public void replaceAvailableLpu(List<ProgramLpu.Row> rows, Instant now) {
        availableLpu.clear();
        for (ProgramLpu.Row row : rows) {
            availableLpu.add(ProgramLpu.of(this, row, now));
        }
    }

    /** Разобранные поля шапки сообщения ТЗ-6. */
    public record Details(
            @Nullable UUID externalProductId,
            @Nullable UUID externalParentId,
            String name,
            @Nullable String typeProgramm,
            boolean deleteMark) {
    }

    @Nullable
    public UUID externalProductId() {
        return externalProductId;
    }

    @Nullable
    public UUID externalParentId() {
        return externalParentId;
    }

    public String name() {
        return name;
    }

    @Nullable
    public String typeProgramm() {
        return typeProgramm;
    }

    public List<ProgramLpu> availableLpu() {
        return Collections.unmodifiableList(availableLpu);
    }
}

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
 * Страховая программа РВЛ вместе с перечнем учреждений, где по ней обслуживают.
 *
 * <p>Перечень приходит табличной частью сообщения и замещается целиком: 1С присылает актуальный
 * состав, а дописывание сохранило бы навсегда строки, которых в 1С уже нет. Строки принадлежат
 * программе: строка, выбывшая из перечня, удаляется из базы.
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
     * Коллекция не final: спецификация Jakarta Persistence (раздел 2.1) запрещает final у сохраняемых
     * полей сущности, а Hibernate при загрузке подменяет коллекцию своей реализацией.
     */
    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramLpu> availableLpu = new ArrayList<>();

    protected Program() {
        // JPA
    }

    private Program(UUID externalId, Instant now) {
        super(externalId, now);
    }

    /** Новая запись из первого сообщения 1С. */
    public static Program create(UUID externalId, Details details, Instant now) {
        Program program = new Program(externalId, now);
        program.applySnapshot(details, now);
        return program;
    }

    public void applySnapshot(Details details, Instant now) {
        this.externalProductId = details.externalProductId();
        this.externalParentId = details.externalParentId();
        this.name = details.name();
        this.typeProgramm = details.typeProgramm();
        applyCommon(details.deleteMark(), now);
    }

    /** Пришло обновление, в котором программа больше не относится к РВЛ: поля применяются, запись уходит в архив. */
    public void archiveAfterLosingRvl(Details details, Instant now) {
        applySnapshot(details, now);
        archiveCommon(now);
    }

    /** Замещает перечень учреждений целиком: строки, которых нет в сообщении, удаляются. */
    public void replaceAvailableLpu(List<ProgramLpu.Row> rows, Instant now) {
        availableLpu.clear();
        for (ProgramLpu.Row row : rows) {
            availableLpu.add(ProgramLpu.of(this, row, now));
        }
    }

    /** Разобранные поля шапки сообщения о программе. */
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

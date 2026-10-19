package ru.projectx.datareference.entity.catalog;

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

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramLpu> availableLpu = new ArrayList<>();

    protected Program() {
    }

    private Program(UUID externalId, Instant now) {
        super(externalId, now);
    }

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

    public void archiveAfterLosingRvl(Details details, Instant now) {
        applySnapshot(details, now);
        archiveCommon(now);
    }

    public void replaceAvailableLpu(List<ProgramLpu.Row> rows, Instant now) {
        availableLpu.clear();
        for (ProgramLpu.Row row : rows) {
            availableLpu.add(ProgramLpu.of(this, row, now));
        }
    }

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

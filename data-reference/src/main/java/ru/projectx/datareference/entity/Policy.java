package ru.projectx.datareference.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Полис застрахованного — поток ТЗ-1. Отдельной сущности «застрахованное лицо»
 * в контракте нет: полис и есть застрахованный в рамках договора страхования.
 *
 * <p><b>Здесь и только здесь лежат персональные данные сервиса</b>: ФИО, дата рождения,
 * почта. Из этого следуют два правила, которые действуют вокруг этого класса, а не
 * внутри него: ПДн не попадают в лог — ни значением, ни в тексте исключения, — и у тела
 * сообщения, которым полис приехал, есть срок хранения в таблице сырья.
 *
 * <p>Родственники — это <b>отдельные полисы</b>, а не члены семьи внутри одного:
 * связи «чей это родственник» в контракте нет вообще, и поля в маппинге ТЗ-1 тоже нет.
 * Паспортных данных этот поток не приносит — ТЗ-1 прямо пишет, что структура их потока
 * не определена, поэтому таблицы для них здесь нет.
 *
 * <p>Обе табличные части <b>замещаются целиком</b>: сообщение всегда несёт полный состав
 * строк, и строка, пропавшая из сообщения, обязана пропасть из базы. Слить старые
 * с новыми значило бы накапливать программы, которых у человека уже нет.
 */
@Entity
@Table(name = "policies")
public class Policy extends OneCEntity {

    /**
     * Договор страхования, по которому выдан полис. Логическая ссылка GUID'ом,
     * без внешнего ключа: поток договоров страхования (ТЗ-2) на контуре не создан,
     * и требовать существования строки, которой неоткуда взяться, нельзя.
     */
    @Column(name = "external_contract_id", nullable = false)
    private UUID externalContractId;

    @Column(nullable = false)
    private String number;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Nullable
    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Nullable
    private String gender;

    /** Тот адрес, которым человек входит в личный кабинет. Может отсутствовать. */
    @Nullable
    private String email;

    @Column(name = "begin_date", nullable = false)
    private LocalDate beginDate;

    /** {@code null} — открытая дата источника, а не «полис без срока». */
    @Nullable
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean availability;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PolicyProgram> programs = new ArrayList<>();

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PolicyServiceLine> serviceLines = new ArrayList<>();

    protected Policy() {
        // JPA
    }

    private Policy(UUID externalId, Snapshot snapshot, Instant now) {
        super(externalId, now);
        applySnapshot(snapshot, now);
    }

    public static Policy create(UUID externalId, Snapshot snapshot, Instant now) {
        return new Policy(externalId, snapshot, now);
    }

    public void applySnapshot(Snapshot snapshot, Instant now) {
        Details details = snapshot.details();
        this.externalContractId = details.externalContractId();
        this.number = details.number();
        this.lastName = details.lastName();
        this.firstName = details.firstName();
        this.middleName = details.middleName();
        this.dateOfBirth = details.dateOfBirth();
        this.gender = details.gender();
        this.email = details.email();
        this.beginDate = details.beginDate();
        this.endDate = details.endDate();
        this.availability = details.availability();
        replaceTableParts(snapshot, now);
        applyCommon(details.deleteMark(), now);
    }

    /**
     * Обычный цикл, а не стрим: он наполняет коллекции сущности, то есть имеет побочный
     * эффект по определению.
     */
    private void replaceTableParts(Snapshot snapshot, Instant now) {
        programs.clear();
        for (PolicyProgram.Row row : snapshot.programs()) {
            programs.add(PolicyProgram.of(this, row, now));
        }
        serviceLines.clear();
        for (PolicyServiceLine.Row row : snapshot.serviceLines()) {
            serviceLines.add(PolicyServiceLine.of(this, row, now));
        }
    }

    /**
     * Разобранное сообщение ТЗ-1 целиком: поля полиса и обе табличные части.
     *
     * <p>Одной записью, а не тремя аргументами, потому что применяются они только
     * вместе — замещение табличных частей без обновления полиса оставило бы запись,
     * у которой версия уже новая, а программы ещё старые.
     */
    public record Snapshot(
            Details details,
            List<PolicyProgram.Row> programs,
            List<PolicyServiceLine.Row> serviceLines) {

        public Snapshot {
            programs = List.copyOf(programs);
            serviceLines = List.copyOf(serviceLines);
        }
    }

    /** Поля самого полиса. Разбор — в маппере, инварианты — здесь. */
    public record Details(
            UUID externalContractId,
            String number,
            String lastName,
            String firstName,
            @Nullable String middleName,
            LocalDate dateOfBirth,
            @Nullable String gender,
            @Nullable String email,
            LocalDate beginDate,
            @Nullable LocalDate endDate,
            boolean availability,
            boolean deleteMark) {
    }

    public UUID externalContractId() {
        return externalContractId;
    }

    public String number() {
        return number;
    }

    public String lastName() {
        return lastName;
    }

    public String firstName() {
        return firstName;
    }

    @Nullable
    public String middleName() {
        return middleName;
    }

    public LocalDate dateOfBirth() {
        return dateOfBirth;
    }

    @Nullable
    public String gender() {
        return gender;
    }

    @Nullable
    public String email() {
        return email;
    }

    public LocalDate beginDate() {
        return beginDate;
    }

    @Nullable
    public LocalDate endDate() {
        return endDate;
    }

    public boolean availability() {
        return availability;
    }

    public List<PolicyProgram> programs() {
        return Collections.unmodifiableList(programs);
    }

    public List<PolicyServiceLine> serviceLines() {
        return Collections.unmodifiableList(serviceLines);
    }
}

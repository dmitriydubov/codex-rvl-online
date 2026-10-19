package ru.projectx.datareference.entity.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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

    @Nullable
    private String region;

    @Nullable
    private String address;

    protected Lpu() {
    }

    private Lpu(UUID externalId, Instant now) {
        super(externalId, now);
    }

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

    public void applyContacts(@Nullable String address, @Nullable String region, Instant now) {
        this.address = address;
        this.region = region;
        applyCommon(isArchive(), now);
    }

    public void archiveAfterLosingRvl(Details details, Instant now) {
        applySnapshot(details, now);
        archiveCommon(now);
    }

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

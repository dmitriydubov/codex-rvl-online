package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.Insurer;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.repository.InsurerRepository;
import ru.projectx.datareference.repository.LpuRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class ContragentMapper implements StreamMapper {

    private static final String ACTIVITIES = "typesActivities";
    private static final String ACTIVITY_GUID = "guidTypeActivities";
    private static final String RVL_ACTIVITY_NOT_CANONICAL =
            "настройка projectx.onec.contract.rvl-activity-id не в канонической записи GUID 8-4-4-4-12 "
                    + "или нулевая: без неё санатории РВЛ не отличить от прочих контрагентов 1С:УСК";
    private static final String RVL_ACTIVITY_NOT_SET =
            "не задан projectx.onec.contract.rvl-activity-id — отличить санаторий от прочих юрлиц 1С:УСК нечем";
    private static final String WITHOUT_RVL = "контрагент без вида деятельности РВЛ";
    private static final String NOT_A_SANATORIUM =
            "контрагент не ЛПУ (thisIsLPU = false) — сохранён в справочник страхователей";

    private final LpuRepository lpuRepository;
    private final InsurerRepository insurerRepository;
    private final ForeignReferences foreign;
    private final @Nullable UUID rvlActivity;
    private final Clock clock;

    public ContragentMapper(
            LpuRepository lpuRepository,
            InsurerRepository insurerRepository,
            ForeignReferences foreign,
            OneCContractProperties contract,
            Clock clock) {

        this.lpuRepository = lpuRepository;
        this.insurerRepository = insurerRepository;
        this.foreign = foreign;
        this.rvlActivity = rvlActivityOf(contract);
        this.clock = clock;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.CONTRAGENTS;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        UUID externalId = OneCValues.requiredUuid(data, "guid");
        return OneCValues.flag(data, "thisIsLPU")
                ? applySanatorium(externalId, data)
                : applyInsurer(externalId, data);
    }

    private static @Nullable UUID rvlActivityOf(OneCContractProperties contract) {
        return contract.rvlActivity()
                .map(setting -> OneCGuid.parse(setting)
                        .filter(guid -> !OneCGuid.isEmpty(guid))
                        .orElseThrow(() -> new IllegalStateException(RVL_ACTIVITY_NOT_CANONICAL)))
                .orElse(null);
    }

    private ImportResult applySanatorium(UUID externalId, ObjectNode data) {
        return Optional.ofNullable(rvlActivity)
                .map(rvl -> applySanatorium(externalId, data, rvl))
                .orElseGet(() -> ImportResult.skipped(RVL_ACTIVITY_NOT_SET));
    }

    private ImportResult applySanatorium(UUID externalId, ObjectNode data, UUID rvl) {
        Lpu.Details details = lpuDetailsOf(data);
        Optional<Lpu> known = lpuRepository.findByExternalId(externalId);
        if (!hasActivity(data, rvl)) {
            return known
                    .map(lpu -> archiveAfterLosingRvl(lpu, details))
                    .orElseGet(() -> foreign.skipAsForeign(ParentKind.CONTRAGENT, externalId, WITHOUT_RVL));
        }
        Lpu lpu = known
                .map(existing -> refreshed(existing, details))
                .orElseGet(() -> Lpu.create(externalId, details, clock.instant()));
        lpuRepository.save(lpu);
        return ImportResult.applied();
    }

    private ImportResult archiveAfterLosingRvl(Lpu lpu, Lpu.Details details) {
        lpu.archiveAfterLosingRvl(details, clock.instant());
        lpuRepository.save(lpu);
        return ImportResult.applied();
    }

    private Lpu refreshed(Lpu lpu, Lpu.Details details) {
        lpu.applySnapshot(details, clock.instant());
        return lpu;
    }

    private ImportResult applyInsurer(UUID externalId, ObjectNode data) {
        Insurer.Details details = insurerDetailsOf(data);
        Instant now = clock.instant();

        Insurer insurer = insurerRepository.findByExternalId(externalId)
                .orElseGet(() -> Insurer.create(externalId, details, now));
        insurer.applySnapshot(details, now);
        insurerRepository.save(insurer);

        return foreign.skipAsForeign(ParentKind.CONTRAGENT, externalId, NOT_A_SANATORIUM);
    }

    private static boolean hasActivity(JsonNode data, UUID activityId) {
        return StreamSupport.stream(data.path(ACTIVITIES).spliterator(), false)
                .map(row -> OneCValues.uuid(row, ACTIVITY_GUID))
                .flatMap(Optional::stream)
                .anyMatch(activityId::equals);
    }

    private static Lpu.Details lpuDetailsOf(ObjectNode data) {
        CommonFields common = CommonFields.of(data);
        return new Lpu.Details(common.name(), common.fullName(), common.inn(), common.kpp(),
                optionalText(data, "Телефон"), optionalText(data, "EMAIL"), common.deleteMark());
    }

    private static Insurer.Details insurerDetailsOf(ObjectNode data) {
        CommonFields common = CommonFields.of(data);
        return new Insurer.Details(common.name(), common.fullName(), common.inn(), common.kpp(), common.deleteMark());
    }

    private static @Nullable String optionalText(ObjectNode data, String field) {
        return OneCValues.text(data, field).orElse(null);
    }

    private record CommonFields(
            String name, @Nullable String fullName, @Nullable String inn, @Nullable String kpp, boolean deleteMark) {

        static CommonFields of(ObjectNode data) {
            return new CommonFields(
                    OneCValues.requiredText(data, "Наименование"),
                    optionalText(data, "НаименованиеПолное"),
                    ContragentRequisite.INN.read(data).orElse(null),
                    ContragentRequisite.KPP.read(data).orElse(null),
                    OneCValues.flag(data, "deleteMark"));
        }
    }
}

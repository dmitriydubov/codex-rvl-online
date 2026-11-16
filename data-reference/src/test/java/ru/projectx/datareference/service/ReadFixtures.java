package ru.projectx.datareference.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.catalog.AppealType;
import ru.projectx.datareference.entity.catalog.ContragentService;
import ru.projectx.datareference.entity.catalog.InsuranceContract;
import ru.projectx.datareference.entity.catalog.Insurer;
import ru.projectx.datareference.entity.catalog.Lpu;
import ru.projectx.datareference.entity.catalog.LpuContract;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.entity.policy.PolicyProgram;
import ru.projectx.datareference.entity.policy.PolicyServiceLine;
import ru.projectx.datareference.entity.catalog.PriceListItem;
import ru.projectx.datareference.entity.catalog.Program;

public final class ReadFixtures {

    public static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    static final LocalDate BEGIN = LocalDate.of(2026, 1, 1);

    private ReadFixtures() {
    }

    public static InsuranceContract contract(UUID externalId, @Nullable UUID externalInsurerId) {
        return InsuranceContract.create(externalId,
                new InsuranceContract.Details("ДМС-0001", BEGIN, null, externalInsurerId, false), NOW);
    }

    public static Insurer insurer(UUID externalId) {
        return Insurer.create(externalId, new Insurer.Details("ООО «Страхователь»", null, null, null, false), NOW);
    }

    public static Program program(UUID externalId, boolean archived) {
        return Program.create(externalId, new Program.Details(null, null, "Программа РВЛ", null, archived), NOW);
    }

    public static Lpu lpu(UUID externalId, boolean archived) {
        return Lpu.create(externalId, new Lpu.Details("Санаторий", null, null, null, null, null, archived), NOW);
    }

    public static LpuContract lpuContract(Lpu lpu, @Nullable UUID mainContractExternalId,
                                          boolean usePricesMainContract) {
        return LpuContract.create(UUID.randomUUID(), lpu, new LpuContract.Details("Д-1", null,
                mainContractExternalId, usePricesMainContract, false, BEGIN, null, false), NOW);
    }

    public static AppealType appealType(UUID externalId, String name) {
        return AppealType.create(externalId,
                new AppealType.Details(null, null, name, null, null, null, false, false, false), NOW);
    }

    public static ContragentService service(LpuContract contract, UUID externalAppealTypeId) {
        return ContragentService.create(UUID.randomUUID(), contract,
                new ContragentService.Details(externalAppealTypeId, "У-1", "Размещение", false), NOW);
    }

    public static PriceListItem price(ContragentService service, String amount) {
        return PriceListItem.of(service,
                new PriceListItem.Row(null, null, null, new BigDecimal(amount), null, BEGIN, null), NOW);
    }

    public static Policy policy(UUID externalContractId, List<UUID> programIds, List<UUID> serviceLineProgramIds) {
        var details = new Policy.Details(externalContractId, "П-0001", "Петров", "Иван", null,
                LocalDate.of(1984, 3, 11), null, null, BEGIN, null, true, false);
        List<PolicyProgram.Row> programs = programIds.stream()
                .map(id -> new PolicyProgram.Row(id, BEGIN, null, null, null, null, null, BigDecimal.TEN, true))
                .toList();
        List<PolicyServiceLine.Row> lines = serviceLineProgramIds.stream()
                .map(id -> new PolicyServiceLine.Row(id, 1, BigDecimal.ONE, BEGIN, null, true))
                .toList();
        return Policy.create(UUID.randomUUID(), new Policy.Snapshot(details, programs, lines), NOW);
    }
}

package ru.projectx.datareference.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.Insurer;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.entity.PolicyProgram;
import ru.projectx.datareference.entity.PolicyServiceLine;
import ru.projectx.datareference.entity.PriceListItem;
import ru.projectx.datareference.entity.Program;

/** Синтетические записи справочников для unit-тестов сервисов чтения: те же фабрики, что у разбора 1С. */
final class ReadFixtures {

    static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    static final LocalDate BEGIN = LocalDate.of(2026, 1, 1);

    private ReadFixtures() {
    }

    static InsuranceContract contract(UUID externalId, @Nullable UUID externalInsurerId) {
        return InsuranceContract.create(externalId,
                new InsuranceContract.Details("ДМС-0001", BEGIN, null, externalInsurerId, false), NOW);
    }

    static Insurer insurer(UUID externalId) {
        return Insurer.create(externalId, new Insurer.Details("ООО «Страхователь»", null, null, null, false), NOW);
    }

    static Program program(UUID externalId, boolean archived) {
        return Program.create(externalId, new Program.Details(null, null, "Программа РВЛ", null, archived), NOW);
    }

    static Lpu lpu(UUID externalId, boolean archived) {
        return Lpu.create(externalId, new Lpu.Details("Санаторий", null, null, null, null, null, archived), NOW);
    }

    /** Главный договор — без ссылки на главный; допсоглашение — со ссылкой и признаком «цены с главного». */
    static LpuContract lpuContract(Lpu lpu, @Nullable UUID mainContractExternalId, boolean usePricesMainContract) {
        return LpuContract.create(UUID.randomUUID(), lpu, new LpuContract.Details("Д-1", null,
                mainContractExternalId, usePricesMainContract, false, BEGIN, null, false), NOW);
    }

    static AppealType appealType(UUID externalId, String name) {
        return AppealType.create(externalId,
                new AppealType.Details(null, null, name, null, null, null, false, false, false), NOW);
    }

    static ContragentService service(LpuContract contract, UUID externalAppealTypeId) {
        return ContragentService.create(UUID.randomUUID(), contract,
                new ContragentService.Details(externalAppealTypeId, "У-1", "Размещение", false), NOW);
    }

    static PriceListItem price(ContragentService service, String amount) {
        return PriceListItem.of(service,
                new PriceListItem.Row(null, null, null, new BigDecimal(amount), null, BEGIN, null), NOW);
    }

    static Policy policy(UUID externalContractId, List<UUID> programIds, List<UUID> serviceLineProgramIds) {
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

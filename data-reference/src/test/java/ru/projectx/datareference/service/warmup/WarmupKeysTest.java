package ru.projectx.datareference.service.warmup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.repository.ReferenceId;
import ru.projectx.datareference.repository.catalog.InsuranceContractKey;

class WarmupKeysTest {

    private static final UUID CONTRACT = UUID.fromString("0b6f6b0e-4f7e-4f43-9d55-0f3a6f1d2c01");
    private static final UUID CONTRACT_GUID = UUID.fromString("77ff17b5-e659-4720-b57f-e8a19c00fcea");
    private static final UUID INSURER_GUID = UUID.fromString("d23f3088-f547-4755-b93e-ae192a89d166");
    private static final UUID SECOND_CONTRACT = UUID.fromString("0b6f6b0e-4f7e-4f43-9d55-0f3a6f1d2c02");
    private static final UUID SECOND_CONTRACT_GUID = UUID.fromString("5a0c3f1e-2b7d-4c8a-9e61-3d4f5a6b7c8d");
    private static final UUID LPU = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    private static final UUID PROGRAM = UUID.fromString("2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f6a");
    private static final UUID POLICY = UUID.fromString("3e4f5a6b-7c8d-4e9f-8a1b-2c3d4e5f6a7b");

    @Test
    void contract_isReadByIdByGuidByInsurerAndThroughItsLpuCatalog() {
        WarmupKeys keys = new WarmupKeys(
                List.of(new InsuranceContractKey(CONTRACT, CONTRACT_GUID, INSURER_GUID)), List.of(), List.of(), List.of());

        assertThat(keys.paths()).containsExactly(
                "/internal/v1/insurance-contracts/" + CONTRACT,
                "/internal/v1/insurance-contracts?external_id=" + CONTRACT_GUID,
                "/internal/v1/lpu?contract_external_id=" + CONTRACT_GUID,
                "/internal/v1/insurance-contracts?insurer_external_id=" + INSURER_GUID);
    }

    @Test
    void contractWithoutInsurer_isNotReadByInsurer() {
        WarmupKeys keys = new WarmupKeys(
                List.of(new InsuranceContractKey(CONTRACT, CONTRACT_GUID, null)), List.of(), List.of(), List.of());

        assertThat(keys.paths()).containsExactly(
                "/internal/v1/insurance-contracts/" + CONTRACT,
                "/internal/v1/insurance-contracts?external_id=" + CONTRACT_GUID,
                "/internal/v1/lpu?contract_external_id=" + CONTRACT_GUID);
    }

    @Test
    void lpu_isReadAsCardServicesAndServicesWithPrices() {
        WarmupKeys keys = new WarmupKeys(List.of(), List.of(new ReferenceId(LPU)), List.of(), List.of());

        assertThat(keys.paths()).containsExactly(
                "/internal/v1/lpu/" + LPU,
                "/internal/v1/lpu/" + LPU + "/services",
                "/internal/v1/lpu/" + LPU + "/services?need_prices=true");
    }

    @Test
    void program_isReadById() {
        WarmupKeys keys = new WarmupKeys(List.of(), List.of(), List.of(new ReferenceId(PROGRAM)), List.of());

        assertThat(keys.paths()).containsExactly("/internal/v1/programs/" + PROGRAM);
    }

    @Test
    void policy_isReadOnlyWithoutPersonalData() {
        WarmupKeys keys = new WarmupKeys(List.of(), List.of(), List.of(), List.of(new ReferenceId(POLICY)));

        assertThat(keys.paths()).containsExactly("/internal/v1/policies/" + POLICY + "/contract-view");
    }

    @Test
    void references_takeTurnsRecordByRecord_soAnEarlyStopStillWarmsEveryKind() {
        WarmupKeys keys = new WarmupKeys(
                List.of(new InsuranceContractKey(CONTRACT, CONTRACT_GUID, null),
                        new InsuranceContractKey(SECOND_CONTRACT, SECOND_CONTRACT_GUID, null)),
                List.of(new ReferenceId(LPU)), List.of(new ReferenceId(PROGRAM)), List.of(new ReferenceId(POLICY)));

        assertThat(keys.paths()).containsExactly(
                "/internal/v1/insurance-contracts/" + CONTRACT,
                "/internal/v1/insurance-contracts?external_id=" + CONTRACT_GUID,
                "/internal/v1/lpu?contract_external_id=" + CONTRACT_GUID,
                "/internal/v1/lpu/" + LPU,
                "/internal/v1/lpu/" + LPU + "/services",
                "/internal/v1/lpu/" + LPU + "/services?need_prices=true",
                "/internal/v1/programs/" + PROGRAM,
                "/internal/v1/policies/" + POLICY + "/contract-view",
                "/internal/v1/insurance-contracts/" + SECOND_CONTRACT,
                "/internal/v1/insurance-contracts?external_id=" + SECOND_CONTRACT_GUID,
                "/internal/v1/lpu?contract_external_id=" + SECOND_CONTRACT_GUID);
    }

    @Test
    void noRecords_noReads() {
        assertThat(new WarmupKeys(List.of(), List.of(), List.of(), List.of()).paths()).isEmpty();
    }
}

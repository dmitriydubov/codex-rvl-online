package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.entity.PolicyProgram;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.service.inbound.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@Transactional
@IntegrationTest
class PolicyImportTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_policies";
    private static final UUID POLICY_ID = UUID.fromString("9f5c1a80-0000-4000-8000-000000000001");
    private static final UUID CONTRACT_ID = UUID.fromString("9f5c1a80-0000-4000-8000-000000000002");
    private static final UUID PROGRAM_ID = UUID.fromString("9f5c1a80-0000-4000-8000-000000000003");

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private ReferenceImportService importService;

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private InboundMessageRepository inboundMessageRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void policyMessage_becomesPolicyRow() {
        importOne(policy(header("2026-01-01", "2026-12-31", true, 1), programs(), services()));

        Policy stored = policyRepository.findByExternalId(POLICY_ID).orElseThrow();
        assertThat(stored.details().number()).isEqualTo("П-001");
        assertThat(stored.details().lastName()).isEqualTo("Иванов");
        assertThat(stored.details().dateOfBirth()).isEqualTo(LocalDate.of(1980, 5, 17));
        assertThat(stored.details().externalContractId()).isEqualTo(CONTRACT_ID);
    }

    @Test
    void validityPeriod_comesFromTheActualHeaderRow() {
        importOne(policy(
                header("2023-01-01", "2023-12-31", true, 1) + "," + header("2024-01-01", "2024-12-31", true, 2),
                programs(), services()));

        Policy stored = policyRepository.findByExternalId(POLICY_ID).orElseThrow();
        assertThat(stored.details().beginDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(stored.details().endDate()).isEqualTo(LocalDate.of(2024, 12, 31));
    }

    @Test
    void policyWithoutActiveHeaderRows_stillGetsItsPeriod() {
        importOne(policy(
                header("2023-01-01", "2023-12-31", false, 1) + "," + header("2024-01-01", "2024-12-31", false, 2),
                programs(), services()));

        Policy stored = policyRepository.findByExternalId(POLICY_ID).orElseThrow();
        assertThat(stored.details().beginDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(stored.details().availability()).isFalse();
    }

    @Test
    void openEndDate_becomesEmpty() {
        importOne(policy(header("2024-01-01", "3000-01-01", true, 1), programs(), services()));

        assertThat(policyRepository.findByExternalId(POLICY_ID).orElseThrow().details().endDate()).isNull();
    }

    @Test
    void email_isTakenFromExtProperties() {
        importOne(policy(header("2026-01-01", "2026-12-31", true, 1), programs(), services()));

        assertThat(policyRepository.findByExternalId(POLICY_ID).orElseThrow().details().email())
                .isEqualTo("ivanov@example.org");
    }

    @Test
    void policyWithoutEmail_isStillStored() {
        importOne("""
                {"id":"%s","idContract":"%s","number":"П-001","lastName":"Иванов","firstName":"Иван",\
                "dateOfBirth":"1980-05-17","header":[%s],"programms":[],"services":[]}"""
                .formatted(POLICY_ID, CONTRACT_ID, header("2024-01-01", "2024-12-31", true, 1)));

        assertThat(policyRepository.findByExternalId(POLICY_ID).orElseThrow().details().email()).isNull();
    }

    @Test
    void tableParts_areStored() {
        importOne(policy(header("2024-01-01", "2024-12-31", true, 1), programs(), services()));

        Policy stored = policyRepository.findByExternalId(POLICY_ID).orElseThrow();
        assertThat(stored.programs()).hasSize(1);
        PolicyProgram program = stored.programs().getFirst();
        assertThat(program.externalProgramId()).isEqualTo(PROGRAM_ID);
        assertThat(program.limitAmount()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(stored.serviceLines()).hasSize(1);
        assertThat(stored.serviceLines().getFirst().copayment()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    void tableParts_areReplacedEntirely() {
        importOne(policy(header("2024-01-01", "2024-12-31", true, 1), programs(), services()));

        importOne(policy(header("2024-01-01", "2024-12-31", true, 1), "", ""));

        Policy stored = policyRepository.findByExternalId(POLICY_ID).orElseThrow();
        assertThat(stored.programs()).isEmpty();
        assertThat(stored.serviceLines()).isEmpty();
    }

    @Test
    void lastMessageWins_replacingSnapshotAndTableParts() {
        importOne(policy(header("2024-01-01", "2024-12-31", true, 1), programs(), services()));

        ImportOutcome result = importOne(policy(header("2020-01-01", "2020-12-31", true, 1), "", ""));

        assertThat(result).isInstanceOf(ImportOutcome.Applied.class);
        Policy stored = policyRepository.findByExternalId(POLICY_ID).orElseThrow();
        assertThat(stored.details().beginDate()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(stored.programs()).isEmpty();
    }

    @Test
    void policyWithoutHeaderRows_isContractViolation() {
        assertThatThrownBy(() -> importOne(policy("", programs(), services())))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageContaining("header");
    }

    @Test
    void contractViolation_namesTheFieldButNotItsValue() {
        String withBrokenBirthDate = """
                {"id":"%s","idContract":"%s","number":"П-001","lastName":"Иванов","firstName":"Иван",\
                "dateOfBirth":"17.05.1980","header":[%s],"programms":[],"services":[]}"""
                .formatted(POLICY_ID, CONTRACT_ID, header("2024-01-01", "2024-12-31", true, 1));

        assertThatThrownBy(() -> importOne(withBrokenBirthDate))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageContaining("dateOfBirth")
                .hasMessageNotContaining("17.05.1980");
    }

    private ImportOutcome importOne(String data) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, OneCStream.POLICIES, QUEUE, envelope(data));
        InboundMessage message = inboundMessageRepository.findById(messageId).orElseThrow();
        return importService.importMessage(message);
    }

    private static String policy(String headerRows, String programRows, String serviceRows) {
        return """
                {"id":"%s","idContract":"%s","number":"П-001","lastName":"Иванов","firstName":"Иван",\
                "middleName":"Иванович","dateOfBirth":"1980-05-17","gender":"М",\
                "extProperties":[{"propertyName":"Адрес элпочты застрахованного",\
                "value":{"value":"ivanov@example.org"}}],\
                "header":[%s],"programms":[%s],"services":[%s]}"""
                .formatted(POLICY_ID, CONTRACT_ID, headerRows, programRows, serviceRows);
    }

    private static String header(String beginDate, String endDate, boolean availability, int rowNumber) {
        return """
                {"rowNumber":%d,"beginDate":"%s","endDate":"%s","availability":%b}"""
                .formatted(rowNumber, beginDate, endDate, availability);
    }

    private static String programs() {
        return """
                {"idProgramm":"%s","beginDate":"2024-01-01","endDate":"2024-12-31","amount":500000.00,\
                "premium":12000.00,"limit":150000.00,"availability":true}""".formatted(PROGRAM_ID);
    }

    private static String services() {
        return """
                {"idProgramm":"%s","layerNumber":1,"copayment":20.00,"beginDate":"2024-01-01",\
                "availability":true}""".formatted(PROGRAM_ID);
    }
}

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
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.entity.PolicyProgram;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * ТЗ-1: полис застрахованного. Фильтров у потока нет, зато есть два места, где ТЗ
 * расходится с наивным чтением маппинга: срок действия берётся из строки шапки,
 * а почта лежит в наборе дополнительных свойств.
 *
 * <p>{@code @Transactional} здесь не ради отката, а ради открытой сессии: проверки
 * читают ленивые табличные части, а {@code open-in-view} у сервиса выключен.
 */
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
        importOne(policy(header("2024-01-01", "2024-12-31", true, 1), programs(), services()));

        Policy stored = policyRepository.findByExternalId(POLICY_ID).orElseThrow();
        assertThat(stored.number()).isEqualTo("П-001");
        assertThat(stored.lastName()).isEqualTo("Иванов");
        assertThat(stored.dateOfBirth()).isEqualTo(LocalDate.of(1980, 5, 17));
        assertThat(stored.externalContractId()).isEqualTo(CONTRACT_ID);
    }

    /**
     * ТЗ-1, п. 1.3: срок действия берётся из строки шапки с наибольшим номером среди
     * действующих — не из первой попавшейся и не из полей самого полиса.
     */
    @Test
    void validityPeriod_comesFromTheActualHeaderRow() {
        importOne(policy(
                header("2023-01-01", "2023-12-31", true, 1) + "," + header("2024-01-01", "2024-12-31", true, 2),
                programs(), services()));

        Policy stored = policyRepository.findByExternalId(POLICY_ID).orElseThrow();
        assertThat(stored.beginDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(stored.endDate()).isEqualTo(LocalDate.of(2024, 12, 31));
    }

    /**
     * У прекращённого полиса действующих строк нет вовсе, а срок его действия всё равно
     * нужно знать. Это нормальное состояние, а не запасной вариант на случай ошибки.
     */
    @Test
    void policyWithoutActiveHeaderRows_stillGetsItsPeriod() {
        importOne(policy(
                header("2023-01-01", "2023-12-31", false, 1) + "," + header("2024-01-01", "2024-12-31", false, 2),
                programs(), services()));

        Policy stored = policyRepository.findByExternalId(POLICY_ID).orElseThrow();
        assertThat(stored.beginDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(stored.availability()).isFalse();
    }

    /** Открытая дата источника — это «пока не заменят», а не тысяча лет. */
    @Test
    void openEndDate_becomesEmpty() {
        importOne(policy(header("2024-01-01", "3000-01-01", true, 1), programs(), services()));

        assertThat(policyRepository.findByExternalId(POLICY_ID).orElseThrow().endDate()).isNull();
    }

    /** Почта лежит в наборе дополнительных свойств, а не отдельным полем. */
    @Test
    void email_isTakenFromExtProperties() {
        importOne(policy(header("2024-01-01", "2024-12-31", true, 1), programs(), services()));

        assertThat(policyRepository.findByExternalId(POLICY_ID).orElseThrow().email())
                .isEqualTo("ivanov@example.org");
    }

    /** Почты может не быть — это значит лишь, что в личный кабинет человек не войдёт. */
    @Test
    void policyWithoutEmail_isStillStored() {
        importOne("""
                {"id":"%s","idContract":"%s","number":"П-001","lastName":"Иванов","firstName":"Иван",\
                "dateOfBirth":"1980-05-17","header":[%s],"programms":[],"services":[]}"""
                .formatted(POLICY_ID, CONTRACT_ID, header("2024-01-01", "2024-12-31", true, 1)));

        assertThat(policyRepository.findByExternalId(POLICY_ID).orElseThrow().email()).isNull();
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

    /**
     * Табличные части замещаются целиком: сообщение всегда несёт полный состав строк,
     * и строка, пропавшая из него, обязана пропасть из базы. Слить старые с новыми
     * значило бы накапливать программы, которых у человека уже нет.
     */
    @Test
    void tableParts_areReplacedEntirely() {
        importOne(policy(header("2024-01-01", "2024-12-31", true, 1), programs(), services()));

        importOne(policy(header("2024-01-01", "2024-12-31", true, 1), "", ""));

        Policy stored = policyRepository.findByExternalId(POLICY_ID).orElseThrow();
        assertThat(stored.programs()).isEmpty();
        assertThat(stored.serviceLines()).isEmpty();
    }

    /**
     * Версий у сообщений нет: применяется последнее полученное, даже если по содержанию
     * оно старее. Здесь это заметно сильнее, чем в справочниках, — вместе со снимком
     * замещаются и табличные части, поэтому программы и услуги полиса пропадают.
     */
    @Test
    void lastMessageWins_replacingSnapshotAndTableParts() {
        importOne(policy(header("2024-01-01", "2024-12-31", true, 1), programs(), services()));

        ImportResult result = importOne(policy(header("2020-01-01", "2020-12-31", true, 1), "", ""));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        Policy stored = policyRepository.findByExternalId(POLICY_ID).orElseThrow();
        assertThat(stored.beginDate()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(stored.programs()).isEmpty();
    }

    /** Без единой строки шапки срок действия полиса брать неоткуда. */
    @Test
    void policyWithoutHeaderRows_isContractViolation() {
        assertThatThrownBy(() -> importOne(policy("", programs(), services())))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageContaining("header");
    }

    /**
     * Персональные данные не должны попадать в текст исключения: он доезжает до лога.
     * Сообщение называет поле, но не значение.
     */
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

    private ImportResult importOne(String data) {
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

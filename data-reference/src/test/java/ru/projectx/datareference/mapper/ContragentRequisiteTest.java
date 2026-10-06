package ru.projectx.datareference.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает правила реквизитов контрагента: ИНН — только цифры 0–9, КПП — девять символов по формату ФНС, где
 * заглавные латинские буквы допустимы только в пятой и шестой позициях. Испорченный реквизит отклоняет сообщение,
 * а текст отказа называет поле и правило, но не значение.
 */
class ContragentRequisiteTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {"7701234567", "770123456789", "0"})
    void innOfDigits_isRead(String inn) {
        assertThat(ContragentRequisite.INN.read(node("ИНН", inn))).contains(inn);
    }

    /** Пробелы по краям отбрасываются до проверки, как у любой строки 1С. */
    @Test
    void innWithSurroundingSpaces_isReadTrimmed() {
        assertThat(ContragentRequisite.INN.read(node("ИНН", " 7701234567 "))).contains("7701234567");
    }

    /** Полноширинная и арабско-индийская цифры — цифры для Unicode, но не для реквизита ФНС. */
    @ParameterizedTest
    @ValueSource(strings = {"77012345A7", "77012345Б7", "7701 234567", "7701-234567", "770123456７",
            "770123456٧"})
    void innWithNonDigit_isContractViolationNamingFieldButNotValue(String inn) {
        assertThatThrownBy(() -> ContragentRequisite.INN.read(node("ИНН", inn)))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле ИНН содержит не только цифры");
    }

    /** Длина проверяется раньше состава: у слишком длинного ИНН с буквой причина — длина. */
    @Test
    void innOverLimitWithLetter_isReportedAsTooLong() {
        assertThatThrownBy(() -> ContragentRequisite.INN.read(node("ИНН", "77012345678A9")))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле ИНН длиннее 12 символов");
    }

    @ParameterizedTest
    @ValueSource(strings = {"770101001", "7701A0001", "77010Z001", "7701AZ001"})
    void kppInFnsFormat_isRead(String kpp) {
        assertThat(ContragentRequisite.KPP.read(node("КПП", kpp))).contains(kpp);
    }

    @Test
    void missingRequisite_isEmpty() {
        assertThat(ContragentRequisite.KPP.read(JSON.createObjectNode())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"7701a0001", "77010А001", "770A01001", "770101A01", "A70101001", "7701010B1",
"7701 0001", "77-101001", "77010100", "7７0101001", "7701٠001"})
    void kppOutOfFnsFormat_isContractViolationNamingFieldButNotValue(String kpp) {
        assertThatThrownBy(() -> ContragentRequisite.KPP.read(node("КПП", kpp)))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле КПП не соответствует формату");
    }

    @Test
    void kppOverLimit_isReportedAsTooLong() {
        assertThatThrownBy(() -> ContragentRequisite.KPP.read(node("КПП", "7701010010")))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле КПП длиннее 9 символов");
    }

    private static ObjectNode node(String field, String value) {
        return JSON.createObjectNode().put(field, value);
    }
}

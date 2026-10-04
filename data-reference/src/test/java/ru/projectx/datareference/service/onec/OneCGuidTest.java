package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Каноническая форма GUID 1С:УСК (Ш13a): 32 шестнадцатеричные цифры ASCII группами 8-4-4-4-12
 * через дефис, оба регистра.
 *
 * <p>Ради неё класс и существует: {@code UUID.fromString} в JDK 21 мягче своего Javadoc. Строку не
 * в форме 8-4-4-4-12 он читает запасным путём через {@code Long.parseLong} и молча превращает
 * искажённое значение в чужой GUID (проверено {@code javap -c java.util.UUID} и прогоном на 21.0.11).
 */
class OneCGuidTest {

    private static final UUID GUID = UUID.fromString("77ff17b5-e659-4720-b57f-e8a19c00fcea");

    @Test
    void canonicalGuid_isRead() {
        assertThat(OneCGuid.parse("77ff17b5-e659-4720-b57f-e8a19c00fcea")).contains(GUID);
    }

    /** Регистр 1С:УСК не задаёт, BNF {@code UUID.toString()} допускает оба: прописные — тот же GUID. */
    @Test
    void uppercaseGuid_isTheSameGuid() {
        assertThat(OneCGuid.parse("77FF17B5-E659-4720-B57F-E8A19C00FCEA")).contains(GUID);
    }

    @Test
    void mixedCaseGuid_isTheSameGuid() {
        assertThat(OneCGuid.parse("77ff17B5-e659-4720-B57f-e8a19c00FCEA")).contains(GUID);
    }

    /** Нулевой GUID — каноническая форма; что он значит («ссылки нет»), решает {@link OneCValues}. */
    @Test
    void zeroGuid_isReadAsZeroGuid() {
        assertThat(OneCGuid.parse("00000000-0000-0000-0000-000000000000")).contains(new UUID(0L, 0L));
    }

    /**
     * Каждое из этих значений {@code UUID.fromString} JDK 21 принимает молча и читает как другой
     * GUID: группы короче 8-4-4-4-12, знак числа, 35 символов, 36 символов со сдвинутыми дефисами,
     * знак внутри группы, цифры и латинская буква не из ASCII (их понимает {@code Character.digit}).
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "1-2-3-4-5",
            "+1-2-3-4-5",
            "0-0-0-0-0",
            "77ff17b5-e659-4720-b57f-e8a19c00fce",
            "077ff17b5-e659-4720-b57f-e8a19c00fce",
            "77ff17b5-e659-4720-b57f-+8a19c00fcea",
            "77ff17b5-e659-4720-b57f-e8a19c00fce５",
            "77ff17b5-e659-4720-b57f-e8a19c00fce٥",
            "77ff17b5-e659-4720-b57f-e8a19c00fceＡ"
    })
    void guidThatJdkReadsLeniently_isRejected(String lenient) {
        assertThat(OneCGuid.parse(lenient)).isEmpty();
    }

    /**
     * Эти значения отклонялись и прежде: 37 символов, не шестнадцатеричная буква, скобки, без
     * дефисов, дефис Юникода U+2010 вместо дефиса ASCII, пустая строка. Пробел и перевод строки по
     * краям — тоже не каноническая форма: у поля сообщения их до разбора обрезает
     * {@link OneCValues#text}, как у любой строки 1С, а {@code message-id} конверт не обрезает, и
     * такой {@code message-id} отклоняется.
     *
     * <p>Отказ — пусто, а не исключение {@code UUID.fromString}: его планировщик разбора счёл бы
     * временной ошибкой и повторял бы сообщение, а приём из брокера не поймал бы вовсе. Перевод
     * строки в конце пропустил бы к нему шаблон с {@code ^…$} и {@code find()} ({@code $} допускает
     * завершающий перевод строки), дефис Юникода — шаблон с любым разделителем групп.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "77ff17b5-e659-4720-b57f-e8a19c00fcea0",
            "77ff17b5-e659-4720-b57f-e8a19c00fceg",
            "{77ff17b5-e659-4720-b57f-e8a19c00fcea}",
            "77ff17b5e6594720b57fe8a19c00fcea",
            "77ff17b5\u2010e659\u20104720\u2010b57f\u2010e8a19c00fcea",
            " 77ff17b5-e659-4720-b57f-e8a19c00fcea",
            "77ff17b5-e659-4720-b57f-e8a19c00fcea\n",
            ""
    })
    void notAGuid_isRejected(String notAGuid) {
        assertThat(OneCGuid.parse(notAGuid)).isEmpty();
    }
}

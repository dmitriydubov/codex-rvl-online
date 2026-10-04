package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Защищает каноническую запись GUID из сообщений 1С:УСК: 32 шестнадцатеричные цифры ASCII группами
 * 8-4-4-4-12 через дефис, в любом регистре.
 *
 * <p>Без этой проверки искажённый GUID молча становится чужим. Стандартный {@code UUID.fromString}
 * в JDK 21 читает строку не в форме 8-4-4-4-12 запасным путём через {@code Long.parseLong}. Это видно
 * по байткоду {@code java.util.UUID} и подтверждено прогоном на JDK 21.0.11.
 */
class OneCGuidTest {

    private static final UUID GUID = UUID.fromString("77ff17b5-e659-4720-b57f-e8a19c00fcea");

    @Test
    void canonicalGuid_isRead() {
        assertThat(OneCGuid.parse("77ff17b5-e659-4720-b57f-e8a19c00fcea")).contains(GUID);
    }

    /**
     * 1С:УСК регистр не задаёт, а документация {@code UUID.toString()} допускает оба. Поэтому GUID
     * прописными цифрами — тот же GUID.
     */
    @Test
    void uppercaseGuid_isTheSameGuid() {
        assertThat(OneCGuid.parse("77FF17B5-E659-4720-B57F-E8A19C00FCEA")).contains(GUID);
    }

    @Test
    void mixedCaseGuid_isTheSameGuid() {
        assertThat(OneCGuid.parse("77ff17B5-e659-4720-B57f-e8a19c00FCEA")).contains(GUID);
    }

    /**
     * Нулевой GUID записан канонически и читается как обычный. Что он значит в поле сообщения, решает
     * {@link OneCValues}.
     */
    @Test
    void zeroGuid_isReadAsZeroGuid() {
        assertThat(OneCGuid.parse("00000000-0000-0000-0000-000000000000")).contains(new UUID(0L, 0L));
    }

    /**
     * Каждое из этих значений {@code UUID.fromString} в JDK 21 принимает молча и читает как другой GUID.
     * Здесь группы короче 8-4-4-4-12, знак числа, строка из 35 символов, строка из 36 символов со
     * сдвинутыми дефисами и знак внутри группы. Последние три значения оканчиваются полноширинной
     * цифрой, арабско-индийской цифрой и полноширинной латинской буквой, которые понимает
     * {@code Character.digit}.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "1-2-3-4-5",
            "+1-2-3-4-5",
            "0-0-0-0-0",
            "77ff17b5-e659-4720-b57f-e8a19c00fce",
            "077ff17b5-e659-4720-b57f-e8a19c00fce",
            "77ff17b5-e659-4720-b57f-+8a19c00fcea",
            "77ff17b5-e659-4720-b57f-e8a19c00fce\uFF15",
            "77ff17b5-e659-4720-b57f-e8a19c00fce\u0665",
            "77ff17b5-e659-4720-b57f-e8a19c00fce\uFF21"
    })
    void guidThatJdkReadsLeniently_isRejected(String lenient) {
        assertThat(OneCGuid.parse(lenient)).isEmpty();
    }

    /**
     * Эти значения отклонялись и прежде: 37 символов, не шестнадцатеричная буква, фигурные скобки,
     * запись без дефисов, дефис Юникода U+2010 вместо дефиса ASCII и пустая строка. Пробел и перевод
     * строки по краям тоже не каноническая запись. У поля сообщения их до разбора обрезает
     * {@link OneCValues#text}, как у любой строки 1С, а {@code message-id} конверт не обрезает и такой
     * идентификатор отклоняет.
     *
     * <p>Отказ выражается пустым результатом, а не исключением {@code UUID.fromString}. Такое исключение
     * планировщик разбора принял бы за временную ошибку и повторял бы сообщение, а приём из очереди не
     * поймал бы его вовсе. Перевод строки в конце защищает от шаблона с {@code ^…$} и {@code find()},
     * потому что {@code $} допускает завершающий перевод строки. Дефис Юникода защищает от шаблона,
     * который принимает любой разделитель групп.
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

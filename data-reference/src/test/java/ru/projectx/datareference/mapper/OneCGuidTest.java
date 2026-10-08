package ru.projectx.datareference.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Защищает каноническую запись GUID из сообщений 1С:УСК (8-4-4-4-12, любой регистр). Без неё искажённый
 * GUID молча становится чужим: {@code UUID.fromString} в JDK 21 читает неканоническую строку запасным путём.
 */
class OneCGuidTest {

    private static final UUID GUID = UUID.fromString("77ff17b5-e659-4720-b57f-e8a19c00fcea");

    @Test
    void canonicalGuid_isRead() {
        assertThat(OneCGuid.parse("77ff17b5-e659-4720-b57f-e8a19c00fcea")).contains(GUID);
    }

    /** 1С:УСК регистр не задаёт, а {@code UUID.toString()} допускает оба, поэтому прописные цифры — тот же GUID. */
    @Test
    void uppercaseGuid_isTheSameGuid() {
        assertThat(OneCGuid.parse("77FF17B5-E659-4720-B57F-E8A19C00FCEA")).contains(GUID);
    }

    @Test
    void mixedCaseGuid_isTheSameGuid() {
        assertThat(OneCGuid.parse("77ff17B5-e659-4720-B57f-e8a19c00FCEA")).contains(GUID);
    }

    /** Что нулевой GUID значит в поле сообщения, решает {@link OneCValues}, а здесь он читается как обычный. */
    @Test
    void zeroGuid_isReadAsZeroGuid() {
        assertThat(OneCGuid.parse("00000000-0000-0000-0000-000000000000")).contains(new UUID(0L, 0L));
    }

    /** В выгрузках 1С:УСК нулевой GUID означает пустое значение, и в полях сообщения, и в message-id конверта. */
    @Test
    void zeroGuid_isEmpty() {
        assertThat(OneCGuid.isEmpty(new UUID(0L, 0L))).isTrue();
    }

    @Test
    void realGuid_isNotEmpty() {
        assertThat(OneCGuid.isEmpty(GUID)).isFalse();
    }

    /**
     * Каждое из этих значений {@code UUID.fromString} в JDK 21 молча читает как другой GUID. Последние три
     * оканчиваются полноширинной или арабско-индийской цифрой и полноширинной буквой: их понимает {@code Character.digit}.
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
     * Отказ — пустой результат, а не исключение: исключение планировщик принял бы за временную ошибку и повторял.
     * Перевод строки в конце ловит шаблон с {@code $}, который его допускает, а дефис U+2010 — любой разделитель групп.
     * У полей сообщения пробелы по краям заранее обрезаются, а message-id конверт не обрезает и отклоняет.
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

package ru.projectx.datareference.acceptance;

import java.util.Map;
import java.util.Optional;
import ru.projectx.datareference.entity.inbound.InboundStatus;

final class OneCRequisiteMessages {

    private static final String INN_DIGITS = "770123456789";
    private static final int INN_SPOILED_POSITION = 6;

    private static final Map<String, String> INN_INTRUDERS = Map.of(
            "латинскую букву", "Q",
            "кириллическую букву", "Ж",
            "пробел", " ",
            "дефис", "-",
            "полноширинную цифру", "７",
            "арабско-индийскую цифру", "٧");

    private static final Map<String, String> ACCEPTED_KPP = Map.of(
            "из девяти цифр", "770101001",
            "с латинской буквой в пятой позиции", "7701A1001",
            "с латинской буквой в шестой позиции", "77010Z001",
            "с латинскими буквами в пятой и шестой позициях", "7701AZ001",
            "пустой", "");

    private static final Map<String, String> REJECTED_KPP = Map.of(
            "со строчной латинской буквой в пятой позиции", "7701a1001",
            "с кириллической буквой А в шестой позиции", "77010А001",
            "с латинской буквой в четвёртой позиции", "770A01001",
            "с латинской буквой в седьмой позиции", "770101A01",
            "с пробелом в пятой позиции", "7701 1001",
            "с дефисом в третьей позиции", "77-101001",
            "из восьми цифр", "77010100",
            "с полноширинной цифрой во второй позиции", "7７0101001",
            "с арабско-индийской цифрой в пятой позиции", "7701٠1001");

    private OneCRequisiteMessages() {
    }

    static OneCMessage contragentWithSpoiledInn(boolean sanatorium, String intruder) {
        String inn = INN_DIGITS.substring(0, INN_SPOILED_POSITION) + lookup(INN_INTRUDERS, intruder)
                + INN_DIGITS.substring(INN_SPOILED_POSITION + 1);
        return OneCFailureMessages.contragent(sanatorium, "ИНН", inn, InboundStatus.FAILED);
    }

    static OneCMessage contragentWithKpp(boolean sanatorium, String kind) {
        return Optional.ofNullable(ACCEPTED_KPP.get(kind))
                .map(kpp -> OneCFailureMessages.contragent(sanatorium, "КПП", kpp, accepted(sanatorium)))
                .orElseGet(() -> OneCFailureMessages.contragent(
                        sanatorium, "КПП", lookup(REJECTED_KPP, kind), InboundStatus.FAILED));
    }

    private static InboundStatus accepted(boolean sanatorium) {
        return sanatorium ? InboundStatus.PROCESSED : InboundStatus.SKIPPED;
    }

    private static String lookup(Map<String, String> vocabulary, String word) {
        return Optional.ofNullable(vocabulary.get(word))
                .orElseThrow(() -> new IllegalArgumentException("шаг не знает слова: " + word));
    }
}

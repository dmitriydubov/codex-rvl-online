package ru.projectx.contracts.service.lm;

import ru.projectx.contracts.service.inbox.parse.FieldPresence;
import ru.projectx.contracts.service.inbox.usk.UskResultLayout;
import ru.projectx.contracts.service.usk.UskWording;

public final class LmPolicyTexts {

    static final UskWording WORDING = new UskWording("полис ЛМ", "полиса ЛМ", "полису ЛМ", "результат выпуска",
            "запрос на выпуск полиса ЛМ ещё не отправлялся",
            "справочный сервис недоступен, запрос отложен до следующего прохода",
            "запрос на выпуск полиса ЛМ не подготовлен из-за внутренней ошибки, полис переведён в ERROR",
            "запрос на выпуск полиса ЛМ {} не доставлен, полис уже в статусе {} и не меняется",
            "запрос на выпуск полиса ЛМ {} не доставлен, полис переведён в ERROR");

    public static final UskResultLayout LAYOUT = new UskResultLayout("ISSUED", "policy_usk_id", "policy_usk_number",
            FieldPresence.OPTIONAL, FieldPresence.OPTIONAL);

    private LmPolicyTexts() {
    }
}

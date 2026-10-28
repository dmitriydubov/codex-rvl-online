package ru.projectx.contracts.service.km;

import ru.projectx.contracts.service.inbox.parse.FieldPresence;
import ru.projectx.contracts.service.inbox.usk.UskResultLayout;
import ru.projectx.contracts.service.usk.UskWording;

public final class KmContractTexts {

    public static final UskWording WORDING = new UskWording("договор КМ", "договора КМ", "договору КМ",
            "результат регистрации",
            "передача договора КМ в 1С:УСК ещё не отправлялась",
            "справочный сервис недоступен, передача отложена до следующего прохода",
            "передача договора КМ не подготовлена из-за внутренней ошибки, договор переведён в ERROR",
            "передача договора КМ {} не доставлена, договор уже в статусе {} и не меняется",
            "передача договора КМ {} не доставлена, договор переведён в ERROR");

    static final UskResultLayout LAYOUT = new UskResultLayout("REGISTERED", "contract_usk_id", "contract_usk_number",
            FieldPresence.REQUIRED, FieldPresence.REQUIRED);

    private KmContractTexts() {
    }
}

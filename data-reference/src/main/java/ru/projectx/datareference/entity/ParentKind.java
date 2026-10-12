package ru.projectx.datareference.entity;

public enum ParentKind {

    CONTRAGENT("контрагент"),
    LPU_CONTRACT("договор ЛПУ"),
    CONTRAGENT_SERVICE("услуга");

    private final String title;

    ParentKind(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}

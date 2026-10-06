package ru.projectx.datareference.entity;

/**
 * Вид записи справочника, на которую ссылается сообщение 1С и которую оно может ждать: контрагент для договора и
 * адреса санатория, договор санатория для услуги, услуга для прайса.
 */
public enum ParentKind {

    CONTRAGENT("контрагент"),
    LPU_CONTRACT("договор ЛПУ"),
    CONTRAGENT_SERVICE("услуга");

    private final String title;

    ParentKind(String title) {
        this.title = title;
    }

    /** Как вид записи называется в причине ожидания и отказа. */
    public String title() {
        return title;
    }
}

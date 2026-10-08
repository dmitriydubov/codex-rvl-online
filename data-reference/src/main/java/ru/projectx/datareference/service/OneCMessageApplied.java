package ru.projectx.datareference.service;

import ru.projectx.datareference.entity.OneCStream;

/**
 * Событие о разобранном сообщении 1С:УСК, по которому сбрасываются кэши его потока. Публикуется и при пропуске:
 * страхователь из потока контрагентов записывается и тогда. Ожидающее родителя сообщение событие не публикует.
 */
public record OneCMessageApplied(OneCStream stream) {
}

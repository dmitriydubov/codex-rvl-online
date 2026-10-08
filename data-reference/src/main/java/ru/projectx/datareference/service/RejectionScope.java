package ru.projectx.datareference.service;

import java.util.List;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Окончательный отказ, который разделяют другие сообщения потока, например части одной передачи прайса. Без такой
 * реализации отказ касается только самого сообщения. Работает в транзакции вызывающего.
 */
public interface RejectionScope {

    OneCStream stream();

    /** Возвращает все сообщения, получившие отказ, начиная с {@code message}; причина без значений полей. */
    List<InboundMessage> reject(InboundMessage message, String reason);
}

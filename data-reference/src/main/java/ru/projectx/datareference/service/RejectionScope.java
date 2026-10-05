package ru.projectx.datareference.service;

import java.util.List;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Окончательный отказ сообщения, который разделяют другие сообщения его потока: например, части одной передачи
 * прайса. У потоков без такой реализации отказ касается только самого сообщения.
 *
 * <p>Реализация работает в транзакции вызывающего: отказ всех задетых сообщений фиксируется вместе.
 */
public interface RejectionScope {

    /** Поток, отказы сообщений которого решает реализация. */
    OneCStream stream();

    /**
     * Переводит в отказ сообщение и все сообщения, которые разделяют его исход.
     *
     * @param message сообщение, разбор которого окончательно не удался; его исход ещё не зафиксирован
     * @param reason  причина отказа сообщения, без значений полей
     * @return все сообщения, получившие отказ, начиная с {@code message}
     */
    List<InboundMessage> reject(InboundMessage message, String reason);
}

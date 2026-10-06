package ru.projectx.datareference.service.onec;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.repository.InboundMessageRepository;

/**
 * Чтение частей передачи прайса заранее, до того как разбор части начнёт свою транзакцию и заблокирует строку услуги.
 *
 * <p>Части читаются своей короткой транзакцией только для чтения, отдельно от транзакции разбора, и она завершается,
 * как только части прочитаны. Поэтому тела частей разбираются уже без открытой транзакции: долгий разбор не держит
 * блокировок и не упирается в предел простоя открытой транзакции в базе. Прочитанные части — отдельные копии, их
 * изменение в базу не попадает.
 */
@Component
public class PriceTransferPartsReader {

    private final InboundMessageRepository inbound;

    public PriceTransferPartsReader(InboundMessageRepository inbound) {
        this.inbound = inbound;
    }

    /** Все сообщения передачи {@code cid}, которые уже зафиксированы в сырье, в любом статусе. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<InboundMessage> parts(UUID cid) {
        return inbound.findByCorrelationId(cid);
    }
}

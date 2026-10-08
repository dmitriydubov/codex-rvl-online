package ru.projectx.datareference.service.onec;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.repository.InboundMessageRepository;

/**
 * Чтение частей передачи прайса заранее, своей короткой транзакцией, до блокировки строки услуги. Так долгий разбор
 * тел не держит блокировок и не упирается в предел простоя открытой транзакции. Прочитанные части — отдельные копии,
 * их изменение в базу не попадает.
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

package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Последняя применённая передача прайса по услуге: её финальное сообщение в сырье.
 *
 * <p>Цены услуги определяет передача, финальное сообщение которой принято последним. Передача, финал которой принят
 * раньше этого сообщения, цены не меняет. Момент приёма и идентификатор передачи читаются из строки сырья финального
 * сообщения и здесь не повторяются.
 */
@Entity
@Table(name = "price_list_finalization")
public class PriceListFinalization {

    /** GUID услуги в 1С:УСК. */
    @Id
    @Column(name = "service_external_id", nullable = false, updatable = false)
    private UUID serviceExternalId;

    @Column(name = "final_message_id", nullable = false, updatable = false)
    private UUID finalMessageId;

    protected PriceListFinalization() {
        // JPA
    }

    private PriceListFinalization(UUID serviceExternalId, UUID finalMessageId) {
        this.serviceExternalId = serviceExternalId;
        this.finalMessageId = finalMessageId;
    }

    /** Первая применённая передача по услуге. */
    public static PriceListFinalization of(UUID serviceExternalId, UUID finalMessageId) {
        return new PriceListFinalization(serviceExternalId, finalMessageId);
    }

    public UUID serviceExternalId() {
        return serviceExternalId;
    }

    public UUID finalMessageId() {
        return finalMessageId;
    }
}

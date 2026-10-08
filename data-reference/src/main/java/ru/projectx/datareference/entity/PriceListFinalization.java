package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Последняя применённая передача прайса по услуге. Цены определяет передача, чьё финальное сообщение принято
 * последним; более ранняя цены не меняет. Момент приёма и идентификатор передачи читаются из строки сырья.
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

    public UUID finalMessageId() {
        return finalMessageId;
    }
}

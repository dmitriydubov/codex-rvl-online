package ru.projectx.datareference.entity.inbound;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "price_list_finalization")
public class PriceListFinalization {

    @Id
    @Column(name = "service_external_id", nullable = false, updatable = false)
    private UUID serviceExternalId;

    @Column(name = "final_message_id", nullable = false, updatable = false)
    private UUID finalMessageId;

    protected PriceListFinalization() {
    }

    private PriceListFinalization(UUID serviceExternalId, UUID finalMessageId) {
        this.serviceExternalId = serviceExternalId;
        this.finalMessageId = finalMessageId;
    }

    public static PriceListFinalization of(UUID serviceExternalId, UUID finalMessageId) {
        return new PriceListFinalization(serviceExternalId, finalMessageId);
    }

    public UUID finalMessageId() {
        return finalMessageId;
    }
}

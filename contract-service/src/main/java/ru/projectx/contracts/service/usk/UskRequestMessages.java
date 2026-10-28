package ru.projectx.contracts.service.usk;

import java.time.Instant;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.message.MessageMeta;

public interface UskRequestMessages<R extends IssuanceRecord, F> {

    MessageMeta meta(R record, Instant now);

    String write(MessageMeta meta, R record, F facts);
}

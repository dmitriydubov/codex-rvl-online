package ru.projectx.contracts.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.message.ContractTaskCancellation;
import ru.projectx.contracts.message.TaskEvents;

@Component
public class CancellationUnknownReasons {

    static final String UNKNOWN = "contracts.cancel.reason.unknown";
    static final String EVENT_TYPE = "event_type";

    private static final Logger log = LoggerFactory.getLogger(CancellationUnknownReasons.class);

    private static final String UNKNOWN_CODE = "неизвестный код причины ";
    private static final String OUT_OF_FORMAT_LABEL = "не по формату кода";
    private static final String OUT_OF_FORMAT_REASON = "код причины не по формату кода";
    private static final String APPLIED_AS_OTHER = ", прекращение применено с причиной OTHER";
    private static final String COMMENT_SEPARATOR = "; ";

    private final Counter unknown;

    public CancellationUnknownReasons(MeterRegistry registry) {
        this.unknown = Counter.builder(UNKNOWN)
                .description("прекращения работы с кодом причины вне словаря, применённые с причиной OTHER")
                .tag(EVENT_TYPE, TaskEvents.CONTRACT_TASK_CANCELLED)
                .register(registry);
    }

    public Cancellation asOther(ContractTaskCancellation cancellation) {
        Optional<String> code = CancelReasons.wellFormed(cancellation.cancelReason());
        log.warn("прекращение работы по заявке {}: {}{}", cancellation.individualRequestId(),
                code.map(UNKNOWN_CODE::concat).orElse(OUT_OF_FORMAT_REASON), APPLIED_AS_OTHER);
        unknown.increment();
        String note = UNKNOWN_CODE + code.orElse(OUT_OF_FORMAT_LABEL);
        return new Cancellation(CancelReason.OTHER, comment(note, cancellation.cancelComment()),
                cancellation.cancelledAt());
    }

    private static String comment(String note, @Nullable String sent) {
        return Optional.ofNullable(sent).map(text -> note + COMMENT_SEPARATOR + text).orElse(note);
    }
}

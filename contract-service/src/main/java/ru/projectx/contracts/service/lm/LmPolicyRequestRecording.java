package ru.projectx.contracts.service.lm;

import java.time.Clock;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.service.usk.UskRequestRecording;

@Service
public class LmPolicyRequestRecording extends UskRequestRecording<LmPolicy, LmPolicyStatus, LmRequestFacts> {

    public LmPolicyRequestRecording(LmPolicyRepository policies, LmPolicyRequestFacts facts,
            LmPolicyRequestMessages messages, OutboxJournal journal, RequestStatusPublication statuses,
            LeadershipFence fence, Clock clock) {
        super(LmPolicyKind.KIND, policies, facts, messages, journal, statuses, fence, clock);
    }
}

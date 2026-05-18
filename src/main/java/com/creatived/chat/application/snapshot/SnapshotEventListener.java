package com.creatived.chat.application.snapshot;

import com.creatived.chat.application.event.EventCollectedEvent;
import com.creatived.chat.domain.event.ChatEventRepository;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.infrastructure.config.SnapshotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class SnapshotEventListener {

    private final ChatEventRepository chatEventRepository;
    private final SnapshotApplicationService snapshotApplicationService;
    private final SnapshotProperties snapshotProperties;

    @Async("snapshotTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventCollected(EventCollectedEvent event) {
        SessionId sessionId = event.getChatEvent().getSessionId();

        try {
            long count = chatEventRepository.countBySessionId(sessionId);
            if (count % snapshotProperties.interval() == 0) {
                snapshotApplicationService.createSnapshot(sessionId);
                log.info("Snapshot 생성 완료: sessionId={}, eventCount={}", sessionId.value(), count);
            }
        } catch (Exception e) {
            log.warn("Snapshot 생성 실패 (비즈니스 미차단): sessionId={}", sessionId.value(), e);
        }
    }
}

package com.creatived.chat.presentation.rest;

import com.creatived.chat.application.snapshot.RestoreTimelineQuery;
import com.creatived.chat.application.snapshot.SnapshotApplicationService;
import com.creatived.chat.application.snapshot.TimelineResult;
import com.creatived.chat.presentation.rest.dto.TimelineResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Tag(name = "Timeline", description = "타임라인 복원 API")
@RestController
@RequestMapping("/sessions/{sessionId}/timeline")
@RequiredArgsConstructor
public class TimelineController {

    private final SnapshotApplicationService snapshotApplicationService;

    @Operation(summary = "특정 시점 세션 상태 복원")
    @GetMapping
    public ResponseEntity<TimelineResponse> restoreAt(
            @PathVariable UUID sessionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        TimelineResult result = snapshotApplicationService.restoreAt(
                new RestoreTimelineQuery(sessionId, at));
        return ResponseEntity.ok(
                TimelineResponse.of(sessionId, result.state(), result.restoredFrom(), at));
    }
}

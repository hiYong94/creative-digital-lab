package com.creatived.chat.presentation.rest;

import com.creatived.chat.application.session.*;
import com.creatived.chat.domain.session.SessionStatus;
import com.creatived.chat.infrastructure.redis.PresenceStore;
import com.creatived.chat.presentation.rest.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Tag(name = "Session", description = "세션 관리 API")
@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
@Validated
public class SessionController {

    private final SessionApplicationService sessionApplicationService;
    private final PresenceStore presenceStore;

    @Operation(summary = "세션 생성")
    @PostMapping
    public ResponseEntity<SessionResponse> create(@Valid @RequestBody CreateSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SessionResponse.from(
                        sessionApplicationService.create(new CreateSessionCommand(request.userId()))
                ));
    }

    @Operation(summary = "세션 참여")
    @PostMapping("/{id}/participants")
    public ResponseEntity<SessionResponse> join(
            @PathVariable UUID id,
            @Valid @RequestBody JoinSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SessionResponse.from(
                        sessionApplicationService.join(new JoinSessionCommand(id, request.userId()))
                ));
    }

    @Operation(summary = "세션 종료")
    @DeleteMapping("/{id}")
    public ResponseEntity<SessionResponse> end(@PathVariable UUID id) {
        return ResponseEntity.ok(SessionResponse.from(
                sessionApplicationService.end(new EndSessionCommand(id))
        ));
    }

    @Operation(summary = "세션 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(SessionResponse.from(
                sessionApplicationService.getById(new GetSessionQuery(id))
        ));
    }

    @Operation(summary = "세션 목록 조회")
    @GetMapping
    public ResponseEntity<SessionListResponse> getList(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(SessionListResponse.from(
                sessionApplicationService.getList(new GetSessionListQuery(page, size, status, from, to)),
                page,
                size
        ));
    }

    @Operation(summary = "참여자 온라인 상태 조회")
    @GetMapping("/{id}/participants/{userId}/online")
    public ResponseEntity<OnlineStatusResponse> getOnlineStatus(
            @PathVariable UUID id,
            @PathVariable String userId) {
        boolean online = presenceStore.isOnline(id.toString(), userId);
        return ResponseEntity.ok(OnlineStatusResponse.of(userId, online));
    }
}

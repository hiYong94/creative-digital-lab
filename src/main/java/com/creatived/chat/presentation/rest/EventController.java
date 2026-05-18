package com.creatived.chat.presentation.rest;

import com.creatived.chat.application.event.CollectEventCommand;
import com.creatived.chat.application.event.EventApplicationService;
import com.creatived.chat.application.event.GetEventsByPeriodQuery;
import com.creatived.chat.presentation.rest.dto.CollectEventRequest;
import com.creatived.chat.presentation.rest.dto.CollectEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Event", description = "이벤트 수집 API")
@RestController
@RequestMapping("/sessions/{sessionId}/events")
@RequiredArgsConstructor
public class EventController {

    private final EventApplicationService eventApplicationService;

    @Operation(summary = "이벤트 수집")
    @PostMapping
    public ResponseEntity<CollectEventResponse> collect(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CollectEventRequest request) {
        CollectEventCommand command = new CollectEventCommand(
                sessionId,
                request.userId(),
                request.clientEventId(),
                request.type(),
                request.payload(),
                request.sequenceNo()
        );
        return ResponseEntity.ok(CollectEventResponse.from(
                eventApplicationService.collect(command)
        ));
    }

    @Operation(summary = "이벤트 기간 조회 (디버깅용)")
    @GetMapping
    public ResponseEntity<List<CollectEventResponse>> findByPeriod(
            @PathVariable UUID sessionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<CollectEventResponse> events = eventApplicationService
                .findByPeriod(new GetEventsByPeriodQuery(sessionId, from, to))
                .stream()
                .map(CollectEventResponse::from)
                .toList();
        return ResponseEntity.ok(events);
    }
}

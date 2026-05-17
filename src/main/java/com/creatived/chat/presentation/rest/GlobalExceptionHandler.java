package com.creatived.chat.presentation.rest;

import com.creatived.chat.domain.event.UnsupportedEventTypeException;
import com.creatived.chat.domain.session.AlreadyJoinedException;
import com.creatived.chat.domain.session.InvalidSessionStateException;
import com.creatived.chat.domain.session.ParticipantNotFoundException;
import com.creatived.chat.domain.session.SessionCapacityExceededException;
import com.creatived.chat.domain.session.SessionNotFoundException;
import com.creatived.chat.presentation.rest.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(SessionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "SESSION_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(AlreadyJoinedException.class)
    public ResponseEntity<ErrorResponse> handleConflict(AlreadyJoinedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "ALREADY_JOINED", e.getMessage()));
    }

    @ExceptionHandler(SessionCapacityExceededException.class)
    public ResponseEntity<ErrorResponse> handleCapacityExceeded(SessionCapacityExceededException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "SESSION_CAPACITY_EXCEEDED", e.getMessage()));
    }

    @ExceptionHandler(InvalidSessionStateException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessable(InvalidSessionStateException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(422, "INVALID_SESSION_STATE", e.getMessage()));
    }

    @ExceptionHandler(UnsupportedEventTypeException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(UnsupportedEventTypeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "UNSUPPORTED_EVENT_TYPE", e.getMessage()));
    }

    @ExceptionHandler(ParticipantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleParticipantNotFound(ParticipantNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "PARTICIPANT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "VALIDATION_FAILED", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "VALIDATION_FAILED", message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "올바르지 않은 형식입니다. " + e.getName() + "=" + e.getValue();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "INVALID_PARAMETER_TYPE", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        log.error("예상하지 못한 오류가 발생했습니다.", e);
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of(500, "INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }
}

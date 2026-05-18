package pl.commercelink.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import pl.commercelink.starter.dynamodb.OptimisticLockingExhaustedException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OptimisticLockingExhaustedException.class)
    public ResponseEntity<String> handleOptimisticLockingExhausted(OptimisticLockingExhaustedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .contentType(MediaType.TEXT_PLAIN)
                .body("Another user just modified this data. Please refresh the page and try again.");
    }
}

package pl.commercelink.exception;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import pl.commercelink.starter.dynamodb.OptimisticLockingExhaustedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleOptimisticLockingExhausted returns 409 Conflict with Retry-After header and user-facing message")
    void handleOptimisticLockingExhaustedReturnsConflictResponseWithRetryAfterHeader() {
        // given
        OptimisticLockingExhaustedException exception =
                new OptimisticLockingExhaustedException(3, new ConditionalCheckFailedException("test"));

        // when
        ResponseEntity<String> response = handler.handleOptimisticLockingExhausted(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).contains("Another user just modified this data");
    }
}

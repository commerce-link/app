package pl.commercelink.migration;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** V021 runs on every start: a DynamoDB failure is logged and retried by the next start, never a failed start-up. */
@ExtendWith(MockitoExtension.class)
class V021AddRmaListSortKeyIndexFailureTest {

    @Mock
    AmazonDynamoDB dynamoDB;

    @Test
    void aFailingScanAndDescribeDoNotStopTheApplication() {
        // given
        when(dynamoDB.scan(any(ScanRequest.class))).thenThrow(new AmazonServiceException("throttled"));
        when(dynamoDB.describeTable(anyString())).thenThrow(new AmazonServiceException("denied"));
        V021_AddRmaListSortKeyIndex migration = new V021_AddRmaListSortKeyIndex(dynamoDB, millis -> { }, Duration.ofSeconds(1));

        // when / then
        assertThatCode(migration::execute).doesNotThrowAnyException();
        verify(dynamoDB).describeTable("RMA");
    }
}

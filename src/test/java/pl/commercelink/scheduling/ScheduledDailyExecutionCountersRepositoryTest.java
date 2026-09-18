package pl.commercelink.scheduling;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduledDailyExecutionCountersRepositoryTest {

    @Mock
    private AmazonDynamoDB amazonDynamoDB;

    @InjectMocks
    private ScheduledDailyExecutionCountersRepository repository;

    @Test
    void oneAtomicCallRaisesTheTypeTotalAndTheIntegrationCounterTogether() {
        // given
        ArgumentCaptor<UpdateItemRequest> request = ArgumentCaptor.forClass(UpdateItemRequest.class);

        // when
        repository.increment("store-1", LocalDate.of(2026, 9, 17), ScheduledExecution.ORDERS_IMPORT, "Allegro");

        // then
        verify(amazonDynamoDB).updateItem(request.capture());
        UpdateItemRequest sent = request.getValue();
        assertThat(sent.getTableName()).isEqualTo("ScheduledDailyExecutionCounters");
        assertThat(sent.getKey()).isEqualTo(Map.of(
                "storeId", new AttributeValue().withS("store-1"),
                "executionDate", new AttributeValue().withS("2026-09-17")));
        assertThat(sent.getUpdateExpression()).isEqualTo("ADD #type :one, #integration :one");
        assertThat(sent.getExpressionAttributeNames()).containsOnly(
                Map.entry("#type", "ordersImport"),
                Map.entry("#integration", "ordersImport#Allegro"));
        assertThat(sent.getExpressionAttributeValues()).containsOnly(Map.entry(":one", new AttributeValue().withN("1")));
    }
}

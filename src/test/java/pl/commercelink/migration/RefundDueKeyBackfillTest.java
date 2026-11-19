package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Payment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** How the backfill behaves when an order is saved by another instance while it runs. */
@ExtendWith(MockitoExtension.class)
class RefundDueKeyBackfillTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    @Mock
    private AmazonDynamoDB dynamoDB;

    /** A cancelled order holding a payment as it is stored by a version that does not write the key. */
    private Map<String, AttributeValue> storedWithoutKey(long version) {
        Order order = new Order("store-1");
        order.setOrderId("o-1");
        order.setStatus(OrderStatus.Cancelled);
        order.setOrderedAt(LocalDateTime.of(2026, 11, 3, 14, 25));
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 500));
        Map<String, AttributeValue> item = new HashMap<>(new DynamoDBMapper(dynamoDB).getTableModel(Order.class).convert(order));
        item.remove(RefundDueKeyBackfill.ATTRIBUTE);
        item.put("version", new AttributeValue().withN(String.valueOf(version)));
        return item;
    }

    private void theIndexListsTheOrder() {
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of(Map.of(
                "storeId", new AttributeValue("store-1"), "orderId", new AttributeValue("o-1")))));
    }

    @Test
    void scansTheOrderDateIndexForTheCancelledOrdersOfTheLastNinetyDays() {
        // given
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of()));

        // when
        new RefundDueKeyBackfill(dynamoDB).run(TODAY);

        // then
        ArgumentCaptor<ScanRequest> scan = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDB).scan(scan.capture());
        assertThat(scan.getValue().getIndexName()).isEqualTo("StoreIdOrderedAtIndex");
        assertThat(scan.getValue().getExpressionAttributeValues().get(":cancelled").getS()).isEqualTo("Cancelled");
        assertThat(scan.getValue().getExpressionAttributeValues().get(":cutoff").getS()).isEqualTo("2026-08-20T00:00");
        verify(dynamoDB, never()).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void anOrderSavedMeanwhileIsReadAgainAndKeyedInsteadOfSkipped() {
        // given: between the read and the write another instance saves the order, still without the key
        theIndexListsTheOrder();
        when(dynamoDB.getItem(any(GetItemRequest.class)))
                .thenReturn(new GetItemResult().withItem(storedWithoutKey(1)))
                .thenReturn(new GetItemResult().withItem(storedWithoutKey(2)));
        when(dynamoDB.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(new ConditionalCheckFailedException("The conditional request failed"))
                .thenReturn(new UpdateItemResult());

        // when
        new RefundDueKeyBackfill(dynamoDB).run(TODAY);

        // then: the second write is guarded on the version the second read saw
        ArgumentCaptor<UpdateItemRequest> writes = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB, times(2)).updateItem(writes.capture());
        UpdateItemRequest second = writes.getAllValues().get(1);
        assertThat(second.getUpdateExpression()).isEqualTo("SET #key = :key");
        assertThat(second.getExpressionAttributeValues().get(":key").getS()).isEqualTo("2026-11-03T14:25:00");
        assertThat(second.getExpressionAttributeValues().get(":v").getN()).isEqualTo("2");
    }

    @Test
    void anOrderThatKeepsChangingDoesNotStopTheStartUp() {
        // given
        theIndexListsTheOrder();
        when(dynamoDB.getItem(any(GetItemRequest.class))).thenReturn(new GetItemResult().withItem(storedWithoutKey(1)));
        when(dynamoDB.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(new ConditionalCheckFailedException("The conditional request failed"));

        // when / then
        assertThatCode(() -> new RefundDueKeyBackfill(dynamoDB).run(TODAY)).doesNotThrowAnyException();
        verify(dynamoDB, times(3)).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void anOrderTheMapperCannotReadIsSkippedAndTheOthersAreStillKeyed() {
        // given: the first record carries a status this version does not know
        Map<String, AttributeValue> unreadable = storedWithoutKey(1);
        unreadable.put("orderId", new AttributeValue("o-bad"));
        unreadable.put("status", new AttributeValue("NotAStatus"));
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of(
                Map.of("storeId", new AttributeValue("store-1"), "orderId", new AttributeValue("o-bad")),
                Map.of("storeId", new AttributeValue("store-1"), "orderId", new AttributeValue("o-1")))));
        when(dynamoDB.getItem(any(GetItemRequest.class))).thenAnswer(call -> {
            GetItemRequest request = call.getArgument(0);
            boolean bad = "o-bad".equals(request.getKey().get("orderId").getS());
            return new GetItemResult().withItem(bad ? unreadable : storedWithoutKey(1));
        });
        when(dynamoDB.updateItem(any(UpdateItemRequest.class))).thenReturn(new UpdateItemResult());

        // when
        new RefundDueKeyBackfill(dynamoDB).run(TODAY);

        // then
        ArgumentCaptor<UpdateItemRequest> writes = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB).updateItem(writes.capture());
        assertThat(writes.getValue().getKey().get("orderId").getS()).isEqualTo("o-1");
    }

    @Test
    void anOrderDeletedSinceTheScanIsLeftAlone() {
        // given
        theIndexListsTheOrder();
        when(dynamoDB.getItem(any(GetItemRequest.class))).thenReturn(new GetItemResult());

        // when
        new RefundDueKeyBackfill(dynamoDB).run(TODAY);

        // then
        verify(dynamoDB, never()).updateItem(any(UpdateItemRequest.class));
    }
}

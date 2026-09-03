package pl.commercelink.inventory.deliveries;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveriesRepositoryTest {

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;
    @Mock
    private PaginatedQueryList<Delivery> paginatedQueryList;
    @Mock
    private PaginatedScanList<Delivery> paginatedScanList;

    private DeliveriesRepository deliveriesRepository;

    @BeforeEach
    void setup() {
        deliveriesRepository = new DeliveriesRepository(amazonDynamoDB);
        ReflectionTestUtils.setField(deliveriesRepository, "dynamoDBMapper", dynamoDBMapper);
    }

    @Test
    @DisplayName("searchActiveDeliveries does not throw when some deliveries have a null estimatedDeliveryAt and sorts them first")
    void searchActiveDeliveriesDoesNotThrowOnNullEstimatedDeliveryAtAndSortsThemFirst() {
        // given
        Delivery pending = delivery("d-pending", null);
        Delivery earlier = delivery("d-earlier", LocalDate.of(2026, 8, 12));
        Delivery later = delivery("d-later", LocalDate.of(2026, 8, 20));
        List<Delivery> deliveries = Arrays.asList(later, pending, earlier);
        when(dynamoDBMapper.query(eq(Delivery.class), any(DynamoDBQueryExpression.class))).thenReturn(paginatedQueryList);
        when(paginatedQueryList.iterator()).thenReturn(deliveries.iterator());
        DeliveryFilter filter = new DeliveryFilter(null, null, null, null, null, true, false, false, false, false);

        // when
        List<Delivery> result = deliveriesRepository.searchActiveDeliveries("store-1", filter, 1, 25);

        // then
        assertThat(result).extracting(Delivery::getDeliveryId)
                .containsExactly("d-pending", "d-earlier", "d-later");
    }

    @Test
    void superAdminScanIsRestrictedToGlobalConnectionDeliveries() {
        // given
        when(dynamoDBMapper.scan(eq(Delivery.class), any(DynamoDBScanExpression.class))).thenReturn(paginatedScanList);
        when(paginatedScanList.iterator()).thenReturn(Collections.<Delivery>emptyList().iterator());
        DeliveryFilter filter = new DeliveryFilter(null, null, null, null, null, true, false, false, false, true);

        // when
        deliveriesRepository.searchActiveDeliveries(filter, 1, 25);

        // then
        ArgumentCaptor<DynamoDBScanExpression> captured = ArgumentCaptor.forClass(DynamoDBScanExpression.class);
        verify(dynamoDBMapper).scan(eq(Delivery.class), captured.capture());
        assertThat(captured.getValue().getFilterExpression()).contains("connectionMode = :globalMode");
        assertThat(captured.getValue().getExpressionAttributeValues().get(":globalMode").getS()).isEqualTo("GLOBAL");
    }

    @Test
    void storeScopedQueryIsNotRestrictedByConnectionMode() {
        // given
        when(dynamoDBMapper.query(eq(Delivery.class), any(DynamoDBQueryExpression.class))).thenReturn(paginatedQueryList);
        when(paginatedQueryList.iterator()).thenReturn(Collections.<Delivery>emptyList().iterator());
        DeliveryFilter filter = new DeliveryFilter(null, null, null, null, null, true, false, false, false, false);

        // when
        deliveriesRepository.searchActiveDeliveries("store-1", filter, 1, 25);

        // then
        ArgumentCaptor<DynamoDBQueryExpression<Delivery>> captured = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        verify(dynamoDBMapper).query(eq(Delivery.class), captured.capture());
        assertThat(captured.getValue().getFilterExpression()).doesNotContain("connectionMode");
    }

    @Test
    void trackableDropshipQueryFiltersOnTypeReceivedOrderStatusExternalIdAndNestedTrackingState() {
        // given
        Delivery withNumber = new Delivery("store-1", "ACME-DS-1", "Acme");
        withNumber.setType(DeliveryType.DROPSHIP);
        Delivery blankNumber = new Delivery("store-1", " ", "Acme");
        blankNumber.setType(DeliveryType.DROPSHIP);
        when(dynamoDBMapper.query(eq(Delivery.class), any(DynamoDBQueryExpression.class))).thenReturn(paginatedQueryList);
        when(paginatedQueryList.stream()).thenReturn(Stream.of(blankNumber, withNumber));

        // when
        List<Delivery> result = deliveriesRepository.findTrackableDropshipDeliveries("store-1");

        // then
        assertThat(result).containsExactly(withNumber);
        ArgumentCaptor<DynamoDBQueryExpression<Delivery>> captured = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        verify(dynamoDBMapper).query(eq(Delivery.class), captured.capture());
        DynamoDBQueryExpression<Delivery> expression = captured.getValue();
        assertThat(expression.getFilterExpression())
                .contains("#type = :dropship")
                .contains("attribute_not_exists(receivedAt) OR receivedAt = :null")
                .contains("attribute_not_exists(orderStatus) OR orderStatus = :null")
                .contains("attribute_exists(externalDeliveryId)")
                .contains("attribute_not_exists(tracking) OR attribute_not_exists(tracking.#st) OR tracking.#st = :pending");
        assertThat(expression.getExpressionAttributeNames()).containsEntry("#type", "type");
        assertThat(expression.getExpressionAttributeNames()).containsEntry("#st", "state");
        assertThat(expression.getExpressionAttributeValues().get(":pending").getS()).isEqualTo("PENDING");
        assertThat(expression.getExpressionAttributeValues().get(":dropship").getS()).isEqualTo("DROPSHIP");
    }

    private Delivery delivery(String deliveryId, LocalDate estimatedDeliveryAt) {
        Delivery delivery = new Delivery();
        delivery.setStoreId("store-1");
        delivery.setDeliveryId(deliveryId);
        delivery.setEstimatedDeliveryAt(estimatedDeliveryAt);
        return delivery;
    }
}

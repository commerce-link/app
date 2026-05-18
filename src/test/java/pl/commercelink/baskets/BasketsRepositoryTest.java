package pl.commercelink.baskets;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BasketsRepositoryTest {

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;
    @Mock
    private PaginatedScanList<Basket> paginatedScanList;

    private BasketsRepository basketsRepository;

    @BeforeEach
    void setup() {
        basketsRepository = spy(new BasketsRepository(amazonDynamoDB));
        ReflectionTestUtils.setField(basketsRepository, "dynamoDBMapper", dynamoDBMapper);
    }

    @Test
    @DisplayName("deleteAllBasketsOlderThan deletes every basket returned by the DynamoDB scan")
    void deleteAllBasketsOlderThanDeletesEveryBasketReturnedByScan() {
        // given
        Basket b1 = basket("b-1");
        Basket b2 = basket("b-2");
        Basket b3 = basket("b-3");
        List<Basket> baskets = Arrays.asList(b1, b2, b3);
        when(dynamoDBMapper.scan(eq(Basket.class), any(DynamoDBScanExpression.class))).thenReturn(paginatedScanList);
        doAnswer(invocation -> {
            Consumer<Basket> action = invocation.getArgument(0);
            baskets.forEach(action);
            return null;
        }).when(paginatedScanList).forEach(any());
        doNothing().when(basketsRepository).delete(any(Basket.class));

        // when
        basketsRepository.deleteAllBasketsOlderThan(LocalDateTime.now());

        // then
        var order = inOrder(basketsRepository);
        order.verify(basketsRepository).delete(b1);
        order.verify(basketsRepository).delete(b2);
        order.verify(basketsRepository).delete(b3);
    }

    private Basket basket(String basketId) {
        Basket basket = new Basket();
        basket.setStoreId("store-1");
        basket.setBasketId(basketId);
        return basket;
    }
}

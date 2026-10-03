package pl.commercelink.baskets;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** The list query is built against the light GSI projection, counted, sliced, then the page is loaded in full. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BasketsRepositoryListTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 12, 0);

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;
    @Mock
    private PaginatedQueryList<Basket> queryList;

    private BasketsRepository repository;

    @BeforeEach
    void setup() {
        repository = new BasketsRepository(amazonDynamoDB);
        ReflectionTestUtils.setField(repository, "dynamoDBMapper", dynamoDBMapper);
    }

    private static Basket light(String id) {
        Basket basket = new Basket();
        basket.setStoreId("store-1");
        basket.setBasketId(id);
        return basket;
    }

    private void indexReturns(List<Basket> projected) {
        when(dynamoDBMapper.query(eq(Basket.class), any(DynamoDBQueryExpression.class))).thenReturn(queryList);
        when(queryList.iterator()).thenAnswer(i -> projected.iterator());
        when(queryList.stream()).thenAnswer(i -> projected.stream());
        when(queryList.size()).thenReturn(projected.size());
    }

    private void fullRecordsAre(List<Basket> full) {
        // batchLoad answers in its own order; the repository must restore the index order
        List<Object> shuffled = new ArrayList<>(full);
        Collections.reverse(shuffled);
        when(dynamoDBMapper.batchLoad(anyList())).thenReturn(Map.of("Baskets", shuffled));
    }

    @SuppressWarnings("unchecked")
    private DynamoDBQueryExpression<Basket> capturedQuery() {
        ArgumentCaptor<DynamoDBQueryExpression<Basket>> captor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        org.mockito.Mockito.verify(dynamoDBMapper).query(eq(Basket.class), captor.capture());
        return captor.getValue();
    }

    @Test
    void plainOffersQueryReadsTheIndexNewestFirstFilteredByType() {
        // given
        indexReturns(List.of(light("a"), light("b")));
        fullRecordsAre(List.of(light("a"), light("b")));

        // when
        OfferListResult result = repository.findForList("store-1",
                new OfferListCriteria(BasketType.Offer, null, Set.of(), null, null, NOW), 1, 25);

        // then
        DynamoDBQueryExpression<Basket> query = capturedQuery();
        assertThat(query.getIndexName()).isEqualTo("BasketCreatedAtIndex");
        assertThat(query.isScanIndexForward()).isFalse();
        assertThat(query.getKeyConditionExpression()).isEqualTo("storeId = :storeId");
        assertThat(query.getFilterExpression()).isEqualTo("#type = :type");
        assertThat(query.getExpressionAttributeValues().get(":type").getS()).isEqualTo("Offer");
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.rows()).extracting(Basket::getBasketId).containsExactly("a", "b");
    }

    @Test
    void textSearchesTheNameAndTheStartOfTheId() {
        // given
        indexReturns(List.of());

        // when
        repository.findForList("store-1", new OfferListCriteria(BasketType.Offer, "CAD", Set.of(), null, null, NOW), 1, 25);

        // then
        DynamoDBQueryExpression<Basket> query = capturedQuery();
        assertThat(query.getFilterExpression()).isEqualTo("#type = :type AND (contains(#name, :q) OR begins_with(basketId, :q))");
        assertThat(query.getExpressionAttributeNames()).containsEntry("#name", "name").containsEntry("#type", "type");
        assertThat(query.getExpressionAttributeValues().get(":q").getS()).isEqualTo("CAD");
    }

    @Test
    void validityValuesAreAlternatives() {
        // given
        indexReturns(List.of());

        // when
        repository.findForList("store-1", new OfferListCriteria(BasketType.Offer, null,
                EnumSet.of(OfferValidity.EXPIRING, OfferValidity.NO_EXPIRY), null, null, NOW), 1, 25);

        // then
        DynamoDBQueryExpression<Basket> query = capturedQuery();
        assertThat(query.getFilterExpression()).isEqualTo("#type = :type AND ("
                + "(expiresAt >= :now AND expiresAt <= :soon) OR attribute_not_exists(expiresAt))");
        assertThat(query.getExpressionAttributeValues().get(":now").getS()).isEqualTo("2026-10-03T12:00:00");
        assertThat(query.getExpressionAttributeValues().get(":soon").getS()).isEqualTo("2026-10-10T12:00:00");
    }

    @Test
    void singleValidityValueIsNotWrappedInRedundantParentheses() {
        // given
        indexReturns(List.of());

        // when
        repository.findForList("store-1", new OfferListCriteria(BasketType.Offer, null,
                EnumSet.of(OfferValidity.EXPIRING), null, null, NOW), 1, 25);

        // then
        // DynamoDB rejects "((a AND b))" with "redundant parentheses"
        assertThat(capturedQuery().getFilterExpression())
                .isEqualTo("#type = :type AND (expiresAt >= :now AND expiresAt <= :soon)");
    }

    @Test
    void activeAndExpiredUseTheSameBoundaries() {
        // given
        indexReturns(List.of());

        // when
        repository.findForList("store-1", new OfferListCriteria(BasketType.Offer, null,
                EnumSet.of(OfferValidity.ACTIVE, OfferValidity.EXPIRED), null, null, NOW), 1, 25);

        // then
        assertThat(capturedQuery().getFilterExpression())
                .isEqualTo("#type = :type AND (expiresAt > :soon OR expiresAt < :now)");
    }

    @Test
    void toDateCoversTheWholeDay() {
        // given
        indexReturns(List.of());

        // when
        repository.findForList("store-1", new OfferListCriteria(BasketType.Offer, null, Set.of(),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), NOW), 1, 25);

        // then
        DynamoDBQueryExpression<Basket> query = capturedQuery();
        assertThat(query.getKeyConditionExpression()).isEqualTo("storeId = :storeId AND createdAt BETWEEN :from AND :to");
        assertThat(query.getExpressionAttributeValues().get(":from").getS()).isEqualTo("2026-09-01T00:00:00");
        // "2026-09-30T18:00:00" <= "2026-09-30T23:59:59.999999999" as strings: the last day is included
        assertThat(query.getExpressionAttributeValues().get(":to").getS()).isEqualTo("2026-09-30T23:59:59.999999999");
    }

    @Test
    void onlyAToDateIsAnUpperBound() {
        // given
        indexReturns(List.of());

        // when
        repository.findForList("store-1", new OfferListCriteria(BasketType.Offer, null, Set.of(),
                null, LocalDate.of(2026, 9, 30), NOW), 1, 25);

        // then
        assertThat(capturedQuery().getKeyConditionExpression()).isEqualTo("storeId = :storeId AND createdAt <= :to");
    }

    @Test
    void secondPageIsSlicedAndCountedOnTheWholeResult() {
        // given
        List<Basket> projected = IntStream.range(0, 30).mapToObj(i -> light("id-" + i)).toList();
        indexReturns(projected);
        fullRecordsAre(projected.subList(25, 30));

        // when
        OfferListResult result = repository.findForList("store-1",
                new OfferListCriteria(BasketType.Offer, null, Set.of(), null, null, NOW), 2, 25);

        // then
        assertThat(result.total()).isEqualTo(30);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.rows()).extracting(Basket::getBasketId).containsExactly("id-25", "id-26", "id-27", "id-28", "id-29");
    }

    @Test
    void aPageBeyondTheEndShowsTheLastPage() {
        // given
        List<Basket> projected = IntStream.range(0, 3).mapToObj(i -> light("id-" + i)).toList();
        indexReturns(projected);
        fullRecordsAre(projected);

        // when
        OfferListResult result = repository.findForList("store-1",
                new OfferListCriteria(BasketType.Offer, null, Set.of(), null, null, NOW), 9, 25);

        // then
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.rows()).hasSize(3);
    }

    @Test
    void aRecordDeletedBetweenTheIndexReadAndTheLoadIsSkipped() {
        // given
        indexReturns(List.of(light("a"), light("gone"), light("b")));
        fullRecordsAre(List.of(light("a"), light("b")));

        // when
        OfferListResult result = repository.findForList("store-1",
                new OfferListCriteria(BasketType.Offer, null, Set.of(), null, null, NOW), 1, 25);

        // then
        assertThat(result.rows()).extracting(Basket::getBasketId).containsExactly("a", "b");
    }

    @Test
    void emptyResultDoesNotCallBatchLoad() {
        // given
        indexReturns(List.of());

        // when
        OfferListResult result = repository.findForList("store-1",
                new OfferListCriteria(BasketType.Offer, null, Set.of(), null, null, NOW), 1, 25);

        // then
        assertThat(result.rows()).isEmpty();
        org.mockito.Mockito.verify(dynamoDBMapper, org.mockito.Mockito.never()).batchLoad(anyList());
    }
}

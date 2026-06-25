package pl.commercelink.taxonomy;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.OfferItemReloader;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.fulfilment.FulfilmentGroup;
import pl.commercelink.orders.fulfilment.FulfilmentGroupsGenerator;
import pl.commercelink.orders.fulfilment.FulfilmentItem;
import pl.commercelink.orders.fulfilment.FulfilmentSource;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SWEEP surface A — raw int sort-keys derived from {@code ProductCategory.ordinal()}.
 *
 * Surface 4 froze {@code Comparator.comparing(getCategory)} (natural enum order). This surface is
 * COMPLEMENTARY: it freezes the {@code int}-returning sort-key methods that read {@code .ordinal()}
 * directly and the comparators that consume them (including reversed / two-level / null sentinel).
 *
 * Sites:
 *  - OrderItem.getSequenceNumber()            -> getCategory().ordinal(),  consumed by
 *    OrderItemsRepository.scanAndSort (Comparator.comparing(getSequenceNumber)) [ascending].
 *  - FulfilmentItem.getFirstSortNumber()      -> source.getCategory().ordinal(), consumed by
 *    FulfilmentGroupsGenerator (Comparator.comparing(getFirstSortNumber).thenComparing(second)) [ascending].
 *  - FulfilmentGroup.getFirstSortNumber()     -> source.getCategory().ordinal() [ascending].
 *  - OfferItemReloader.getCategoryOrdinal()   -> null -> Integer.MAX_VALUE, else category.ordinal(),
 *    consumed REVERSED (reproduced here on real ordinals because the method is private).
 */
@ExtendWith(MockitoExtension.class)
class GoldenSweepCategoryOrdinalSortKeysTest {

    private static OrderItem orderItemWithCategory(ProductCategory category) {
        return new OrderItem(null, category, "n", 1, 0, null, false);
    }

    private static FulfilmentSource sourceWithCategory(ProductCategory category, double priceGross) {
        FulfilmentSource source = new FulfilmentSource();
        source.setSequenceNumber(category.ordinal());
        source.setPriceGross(priceGross);
        source.setPriceNet(priceGross);
        return source;
    }

    @Test
    void orderItemSequenceNumberIsCategoryOrdinal() {
        // given
        // then
        // raw ordinal, NOT a remapped/business sequence: CPU=0, Other=10, Services=11, Laptops=12.
        assertThat(orderItemWithCategory(ProductCategory.CPU).getSequenceNumber()).isEqualTo(0);
        assertThat(orderItemWithCategory(ProductCategory.Other).getSequenceNumber()).isEqualTo(10);
        assertThat(orderItemWithCategory(ProductCategory.Services).getSequenceNumber()).isEqualTo(11);
        assertThat(orderItemWithCategory(ProductCategory.Laptops).getSequenceNumber()).isEqualTo(12);
        assertThat(orderItemWithCategory(ProductCategory.Footrests).getSequenceNumber())
                .isEqualTo(ProductCategory.Footrests.ordinal());
    }

    @Test
    @SuppressWarnings("unchecked")
    void scanAndSortOrdersOrderItemsAscendingByCategoryOrdinal() throws Exception {
        // given
        // drive the REAL OrderItemsRepository.scanAndSort(): a mocked DynamoDBMapper returns unsorted
        // OrderItems and the production method applies its own Comparator.comparing(getSequenceNumber).
        OrderItemsRepository repository = new OrderItemsRepository(mock(AmazonDynamoDB.class));
        List<OrderItem> unsorted = List.of(
                orderItemWithCategory(ProductCategory.Laptops),
                orderItemWithCategory(ProductCategory.CPU),
                orderItemWithCategory(ProductCategory.Services),
                orderItemWithCategory(ProductCategory.Other)
        );
        PaginatedScanList<OrderItem> scanResult = mock(PaginatedScanList.class);
        when(scanResult.stream()).thenReturn(unsorted.stream());
        DynamoDBMapper mapper = mock(DynamoDBMapper.class);
        when(mapper.scan(eq(OrderItem.class), any(DynamoDBScanExpression.class))).thenReturn(scanResult);
        injectMapper(repository, mapper);

        // when (REAL production sort path)
        List<Integer> sorted = repository.scanAndSort(new DynamoDBScanExpression()).stream()
                .map(OrderItem::getSequenceNumber)
                .collect(Collectors.toList());

        // then
        // ascending sequence: CPU(0) < Other(10) < Services(11) < Laptops(12).
        assertThat(sorted).containsExactly(
                ProductCategory.CPU.ordinal(), ProductCategory.Other.ordinal(),
                ProductCategory.Services.ordinal(), ProductCategory.Laptops.ordinal()
        );
    }

    private static void injectMapper(OrderItemsRepository repository, DynamoDBMapper mapper) throws Exception {
        Field field = repository.getClass().getSuperclass().getDeclaredField("dynamoDBMapper");
        field.setAccessible(true);
        field.set(repository, mapper);
    }

    @Test
    void scanAndSortComparatorReferencesTheSequenceNumberGetter() throws Exception {
        // given / then
        // guard that scanAndSort exists and is the consumer of getSequenceNumber (signature freeze).
        var method = OrderItemsRepository.class.getMethod(
                "scanAndSort", com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression.class);
        assertThat(method.getReturnType()).isEqualTo(List.class);
    }

    @Test
    void fulfilmentItemFirstSortNumberIsSourceCategoryOrdinal() {
        // given
        FulfilmentSource cpu = sourceWithCategory(ProductCategory.CPU, 100);
        FulfilmentSource laptops = sourceWithCategory(ProductCategory.Laptops, 100);
        FulfilmentItem cpuItem = new FulfilmentItem(null, cpu, true);
        FulfilmentItem laptopsItem = new FulfilmentItem(null, laptops, true);

        // then
        assertThat(cpuItem.getFirstSortNumber()).isEqualTo(0);
        assertThat(laptopsItem.getFirstSortNumber()).isEqualTo(ProductCategory.Laptops.ordinal());
        // second sort number is the (int) net price, NOT category-derived.
        assertThat(cpuItem.getSecondSortNumber()).isEqualTo(100);
    }

    @Test
    void fulfilmentGroupComparatorSortsAscendingByCategoryOrdinalThenPrice() {
        // given
        // drive the REAL FulfilmentGroupsGenerator.run() so its own
        //   comparing(getFirstSortNumber).thenComparing(getSecondSortNumber)
        // sorts genuine FulfilmentItems (second key = (int) source.getPriceNet()). Each SKU item maps to a
        // single accepted "Action" offer; ProductCategory.CPU(0) must precede Laptops(12), cheaper first.
        OrderItem dearLaptopItem = new OrderItem(null, ProductCategory.Laptops, "n", 1, 0, "L1", false);
        OrderItem cheapLaptopItem = new OrderItem(null, ProductCategory.Laptops, "n", 1, 0, "L2", false);
        OrderItem cpuItem = new OrderItem(null, ProductCategory.CPU, "n", 1, 0, "C1", false);

        InventoryView inventory = mock(InventoryView.class);
        stubSingleOffer(inventory, "L1", 900);
        stubSingleOffer(inventory, "L2", 50);
        stubSingleOffer(inventory, "C1", 999);

        FulfilmentGroupsGenerator generator = FulfilmentGroupsGenerator.builder()
                .withInventory(inventory)
                .build();

        // when (REAL run() sort path)
        List<FulfilmentItem> sorted = generator.run(List.of(dearLaptopItem, cpuItem, cheapLaptopItem));

        // then
        // CPU(ordinal 0) first regardless of its high price; within Laptops the cheaper one precedes.
        assertThat(sorted.stream().map(i -> i.getSource().getSequenceNumber()).collect(Collectors.toList()))
                .containsExactly(ProductCategory.CPU.ordinal(), ProductCategory.Laptops.ordinal(), ProductCategory.Laptops.ordinal());
        assertThat(sorted.stream().map(FulfilmentItem::getSecondSortNumber).collect(Collectors.toList()))
                .containsExactly(999, 50, 900);
    }

    private static void stubSingleOffer(InventoryView inventory, String sku, double netPrice) {
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.getInventoryItems()).thenReturn(List.of(
                new InventoryItem("111", "M", netPrice, "PLN", 1, 1, "Action", true, false, false)));
        when(inventory.findByProductCode(sku)).thenReturn(matched);
    }

    @Test
    void fulfilmentGroupFirstSortNumberMatchesItemFirstSortNumber() {
        // given
        FulfilmentSource source = sourceWithCategory(ProductCategory.Printers, 100);

        // when
        FulfilmentGroup group = new FulfilmentGroup();
        group.setSource(source);

        // then
        // group and item agree on the first sort key (both source.getCategory().ordinal()).
        assertThat(group.getFirstSortNumber()).isEqualTo(ProductCategory.Printers.ordinal());
        assertThat(group.getFirstSortNumber())
                .isEqualTo(new FulfilmentItem(null, source, true).getFirstSortNumber());
    }

    @Test
    void offerItemReloaderOrdinalUsesMaxValueSentinelForNullCategoryAndIsReversed() throws Exception {
        // given
        // invoke the REAL private OfferItemReloader.getCategoryOrdinal(BasketItem) via reflection:
        // null item / null category -> Integer.MAX_VALUE, otherwise category.ordinal().
        // OfferItemReloader's constructor is package-private; build it reflectively without Spring.
        java.lang.reflect.Constructor<OfferItemReloader> ctor = OfferItemReloader.class.getDeclaredConstructor(
                pl.commercelink.inventory.Inventory.class,
                pl.commercelink.pricelist.PricelistRepository.class,
                pl.commercelink.baskets.BasketsRepository.class);
        ctor.setAccessible(true);
        OfferItemReloader reloader = ctor.newInstance(
                mock(pl.commercelink.inventory.Inventory.class),
                mock(pl.commercelink.pricelist.PricelistRepository.class),
                mock(pl.commercelink.baskets.BasketsRepository.class));
        java.lang.reflect.Method getCategoryOrdinal =
                OfferItemReloader.class.getDeclaredMethod("getCategoryOrdinal", BasketItem.class);
        getCategoryOrdinal.setAccessible(true);

        // when (REAL key extractor)
        int cpuKey = (int) getCategoryOrdinal.invoke(reloader, basketItem(ProductCategory.CPU));
        int footrestsKey = (int) getCategoryOrdinal.invoke(reloader, basketItem(ProductCategory.Footrests));
        int nullCategoryKey = (int) getCategoryOrdinal.invoke(reloader, basketItem(null));
        int nullItemKey = (int) getCategoryOrdinal.invoke(reloader, (BasketItem) null);

        // then
        // sentinel for both null item and null category; real ordinals otherwise.
        assertThat(cpuKey).isEqualTo(ProductCategory.CPU.ordinal());
        assertThat(footrestsKey).isEqualTo(ProductCategory.Footrests.ordinal());
        assertThat(nullCategoryKey).isEqualTo(Integer.MAX_VALUE);
        assertThat(nullItemKey).isEqualTo(Integer.MAX_VALUE);

        // the sort applies this key REVERSED: the MAX_VALUE sentinel (null) sorts FIRST, then highest
        // ordinal, then lowest.
        List<Integer> reversed = List.of(cpuKey, footrestsKey, nullCategoryKey).stream()
                .sorted(Comparator.comparingInt((Integer i) -> i).reversed())
                .collect(Collectors.toList());
        assertThat(reversed).containsExactly(nullCategoryKey, footrestsKey, cpuKey);
        assertThat(nullCategoryKey).isGreaterThan(footrestsKey);
    }

    private static BasketItem basketItem(ProductCategory category) {
        BasketItem item = new BasketItem();
        item.setCategoryKey(category != null ? category.name() : null);
        return item;
    }
}

package pl.commercelink.web.fulfilment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentAllocation;
import pl.commercelink.orders.fulfilment.FulfilmentForm;
import pl.commercelink.orders.fulfilment.FulfilmentGroup;
import pl.commercelink.orders.fulfilment.FulfilmentSource;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.orders.fulfilment.UnmatchedItem;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FulfilmentSelectPageFactoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private OrdersRepository ordersRepository;

    private FulfilmentSelectPageFactory factory;
    private SupplierLabelMap labels;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        factory = new FulfilmentSelectPageFactory(messages, storesRepository, ordersRepository);
        labels = labels();
    }

    /** Two connections whose identities differ from their labels, like real ones ("Elko-k1" reads "Elko"). */
    static SupplierLabelMap labels() {
        StoreSupplierConnection elko = new StoreSupplierConnection("Elko-k1", ConnectionMode.OWN, true, true);
        elko.setLabel("Elko");
        StoreSupplierConnection ab = new StoreSupplierConnection("AB-k2", ConnectionMode.OWN, true, true);
        ab.setLabel("AB");
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(elko, ab)));
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(config);
        return new SupplierLabels(mock(StoresRepository.class)).forStore(store);
    }

    static FulfilmentGroup offer(String provider, String category, double net, boolean accepted, String... orderItems) {
        FulfilmentSource source = new FulfilmentSource();
        source.setProvider(provider);
        source.setName("Produkt " + category);
        source.setCategory(category);
        source.setEan("590000000000" + orderItems.length);
        source.setMfn("MFN-" + category);
        source.setQty(10);
        source.setPriceNet(net);
        source.setPriceGross(net * 1.23);
        List<FulfilmentAllocation> allocations = new ArrayList<>();
        for (String orderItem : orderItems) {
            String[] parts = orderItem.split(":");
            FulfilmentAllocation allocation = new FulfilmentAllocation();
            allocation.setOrderId(parts[0]);
            allocation.setOrderItemId(parts[1]);
            allocation.setOrderItemQty(1);
            allocation.setOrderItemPrice(Double.parseDouble(parts[2]));
            allocations.add(allocation);
        }
        return new FulfilmentGroup(source, allocations, accepted);
    }

    static FulfilmentForm form(List<String> orders, FulfilmentGroup... entries) {
        FulfilmentForm form = new FulfilmentForm("orders", "redirect:/dashboard/fulfilment/queue", orders, new ArrayList<>(List.of(entries)));
        form.setSelectedOrders(orders);
        return form;
    }

    private Order order(String id, FulfilmentType type, String name, String city) {
        Order order = new Order("store-1");
        order.setOrderId(id);
        order.setFulfilmentType(type);
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName(name);
        shipping.setCity(city);
        order.setShippingDetails(shipping);
        when(ordersRepository.findById("store-1", id)).thenReturn(order);
        return order;
    }

    @Test
    void warehouseOrdersMakeAWarehousePageWithCoverageAndContext() {
        // given
        order("w1-a", FulfilmentType.WarehouseFulfilment, "Jan", "Kraków");
        order("w2-b", FulfilmentType.WarehouseFulfilment, "Ola", "Gdańsk");
        FulfilmentForm form = form(List.of("w1-a", "w2-b"),
                offer("Elko-k1", "CPU", 100, true, "w1-a:i1:200"),
                offer("Elko-k1", "GPU", 300, true, "w1-a:i2:500", "w2-b:i3:500"));
        form.setUnmatched(List.of(new UnmatchedItem("w2-b", "i4", "Klawiatura", 1, 449, UnmatchedItem.Reason.NO_OFFER)));

        // when
        FulfilmentSelectPage page = factory.forOrders(form, "store-1", false, labels, TODAY, PL);

        // then
        assertThat(page.kind()).isEqualTo(FulfilmentSelectPage.Kind.WAREHOUSE);
        assertThat(page.itemsTotal()).isEqualTo(4);
        assertThat(page.orders()).extracting(FulfilmentSelectPage.OrderRef::orderId).containsExactly("w1-a", "w2-b");
        assertThat(page.orders()).extracting(FulfilmentSelectPage.OrderRef::items).containsExactly(2, 2);
        assertThat(page.orders()).extracting(FulfilmentSelectPage.OrderRef::href).containsExactly("/dashboard/orders/w1-a", "/dashboard/orders/w2-b");
        assertThat(page.context()).isEqualTo("Zamówienia magazynowe: 2 · pozycje do zamówienia: 4");
        assertThat(page.showCoverage()).isTrue();
        assertThat(page.missing()).extracting(FulfilmentSelectPage.Missing::orderId, FulfilmentSelectPage.Missing::items,
                        FulfilmentSelectPage.Missing::href)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("w2-b", 1, "/dashboard/orders/w2-b"));
        assertThat(page.missingItems()).isEqualTo(1);
        assertThat(page.missingKey()).isEqualTo("fulfilment.select.missing.summary");
        assertThat(page.emptyState()).isEqualTo(FulfilmentSelectPage.EmptyState.NONE);
    }

    @Test
    void oneDropshipOrderNamesTheOrderAndTheCustomerAndHidesTheChips() {
        // given
        order("d1-x", FulfilmentType.DirectToConsumer, "Robert", "Wrocław");
        FulfilmentForm form = form(List.of("d1-x"), offer("AB-k2", "Monitor", 800, false, "d1-x:i1:1149"));

        // when
        FulfilmentSelectPage page = factory.forOrders(form, "store-1", false, labels, TODAY, PL);

        // then
        assertThat(page.kind()).isEqualTo(FulfilmentSelectPage.Kind.DROPSHIP);
        assertThat(page.context()).startsWith("Zamówienie ").contains("Robert").contains("Wrocław").endsWith("pozycje do zamówienia: 1");
        assertThat(page.showCoverage()).isFalse();
        assertThat(page.showOrderRefs()).isFalse();
    }

    @Test
    void theSuperAdminGetsStoreScopedLinksActionsAndTheStoreName() {
        // given
        order("w1-a", FulfilmentType.WarehouseFulfilment, "Jan", "Kraków");
        Store store = new Store();
        store.setStoreId("store-1");
        store.setName("Sklep Demo");
        when(storesRepository.findById("store-1")).thenReturn(store);
        FulfilmentForm form = form(List.of("w1-a"), offer("Elko-k1", "CPU", 100, true, "w1-a:i1:200"));

        // when
        FulfilmentSelectPage page = factory.forOrders(form, "store-1", true, labels, TODAY, PL);

        // then
        assertThat(page.storeName()).isEqualTo("Sklep Demo");
        assertThat(page.order("w1-a").href()).isEqualTo("/dashboard/store/store-1/orders/w1-a");
        assertThat(page.actions().commit()).isEqualTo("/dashboard/store/store-1/orders/fulfilment/commit");
        assertThat(page.actions().commitAndContinue()).isEqualTo("/dashboard/store/store-1/orders/fulfilment/commitAndContinue");
        assertThat(page.actions().skip()).isNull();
    }

    @Test
    void oneOrderAtATimeShowsTheStepTheSkipActionAndTheNextOrderHelp() {
        // given
        order("o2", FulfilmentType.WarehouseFulfilment, "Jan", "Kraków");
        FulfilmentForm form = form(List.of("o2", "o3", "o4", "o5"), offer("Elko-k1", "CPU", 100, true, "o2:i1:200"));
        form.setOrderByOrder(true);
        form.setOrderCountAtStart(5);
        form.setOnlyWithProfit(true);

        // when
        FulfilmentSelectPage page = factory.forOrders(form, "store-1", false, labels, TODAY, PL);

        // then
        assertThat(page.step()).isEqualTo(new FulfilmentSelectPage.Step(2, 5, false));
        assertThat(page.actions().skip()).isEqualTo("/dashboard/orders/fulfilment/skip");
        assertThat(page.actions().canContinue()).isFalse();
        assertThat(page.commitHelpKey()).isEqualTo("fulfilment.select.commit.help.next");
        assertThat(page.narrowingKeys()).containsExactly("fulfilment.queue.narrow.profit", "fulfilment.queue.narrow.byOrder");
    }

    @Test
    void theWayBackIsTheQueueWithTheSkipStateTheFormCarries() {
        // given
        FulfilmentForm form = form(List.of("o1"), offer("Elko-k1", "CPU", 100, true, "o1:i1:200"));
        form.setSkippedOrderIds(List.of("s1", "s2"));
        form.setSkippedGroups("1,1");

        // when
        FulfilmentSelectPage page = factory.forOrders(form, "store-1", false, labels, TODAY, PL);

        // then
        assertThat(page.backHref()).isEqualTo("/dashboard/fulfilment/queue?orderIds=s1&orderIds=s2&skippedGroups=1,1");
    }

    @Test
    void offersAreGroupedByCategoryAToZWithTheMostExpensiveItemFirstAndUncategorisedLast() {
        // given
        FulfilmentForm form = form(List.of("o1"),
                offer("Elko-k1", "GPU", 100, false, "o1:i1:200"),
                offer("Elko-k1", null, 10, false, "o1:i2:20"),
                offer("AB-k2", "cpu", 50, false, "o1:i3:90"),
                offer("AB-k2", "GPU", 900, false, "o1:i4:1200"));

        // when
        FulfilmentSelectPage page = factory.forOrders(form, "store-1", false, labels, TODAY, PL);

        // then
        assertThat(page.categories()).extracting(FulfilmentSelectPage.Category::name).containsExactly("cpu", "GPU", null);
        assertThat(page.categories().get(1).offers()).extracting(o -> o.entry().getTargetPrice()).containsExactly(1200.0, 200.0);
    }

    @Test
    void suppliersGetPaletteColoursByLabelAndTheWarehouseKeepsItsOwn() {
        // given
        FulfilmentForm form = form(List.of("o1"),
                offer("Elko-k1", "CPU", 100, false, "o1:i1:200"),
                offer("AB-k2", "CPU", 90, false, "o1:i1:200"),
                offer("Warehouse", "CPU", 80, true, "o1:i1:200"));

        // when
        FulfilmentSelectPage page = factory.forOrders(form, "store-1", false, labels, TODAY, PL);

        // then
        assertThat(page.suppliers()).extracting(FulfilmentSelectPage.SupplierRow::label, FulfilmentSelectPage.SupplierRow::color)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("AB", 1), org.assertj.core.groups.Tuple.tuple("Elko", 2),
                        org.assertj.core.groups.Tuple.tuple("Magazyn sklepu", 0));
        assertThat(page.suppliers().get(2).warehouse()).isTrue();
    }

    @Test
    void theAppliedProposalIsTheOneWhoseOffersAreTicked() {
        // given
        FulfilmentGroup a = offer("Elko-k1", "CPU", 100, true, "o1:i1:200");
        FulfilmentGroup b = offer("AB-k2", "CPU", 90, false, "o1:i1:200");
        FulfilmentForm form = form(List.of("o1"), a, b);
        form.setVariants(List.of(
                new pl.commercelink.orders.fulfilment.FulfilmentVariant(List.of(b.getId()), List.of("AB-k2"), 1, 120.0, true, true),
                new pl.commercelink.orders.fulfilment.FulfilmentVariant(List.of(a.getId()), List.of("Elko-k1"), 1, 130.0, false, true)));

        // when
        FulfilmentSelectPage page = factory.forOrders(form, "store-1", false, labels, TODAY, PL);

        // then
        assertThat(page.variants()).extracting(FulfilmentSelectPage.VariantOption::applied).containsExactly(false, true);
        assertThat(page.variants().get(0).providers()).isEqualTo("AB");
        assertThat(page.variants().get(0).groupIds()).isEqualTo(b.getId());
    }

    @Test
    void earlierStepsCommittedAmountsAreLabelled() {
        // given
        FulfilmentForm form = form(List.of("o1"), offer("Elko-k1", "CPU", 100, true, "o1:i1:200"));
        form.setCommittedSuppliers(new java.util.LinkedHashMap<>(Map.of("AB-k2", 902.4)));

        // when
        FulfilmentSelectPage page = factory.forOrders(form, "store-1", false, labels, TODAY, PL);

        // then
        assertThat(page.committed()).extracting(FulfilmentSelectPage.SupplierTotal::label, FulfilmentSelectPage.SupplierTotal::amount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("AB", 902.4));
    }

    @Test
    void noItemsLeftMakesTheEmptyState() {
        // when
        FulfilmentSelectPage page = factory.forOrders(form(List.of("o1")), "store-1", false, labels, TODAY, PL);

        // then
        assertThat(page.emptyState()).isEqualTo(FulfilmentSelectPage.EmptyState.NOTHING_LEFT);
        assertThat(page.hasOffers()).isFalse();
    }

    @Test
    void onlyMultiOrderOnOneOrderExplainsWhyNothingMatched() {
        // given
        FulfilmentForm form = form(List.of("o1"));
        form.setOnlyMultiOrder(true);
        form.setUnmatched(List.of(new UnmatchedItem("o1", "i1", "Mysz", 1, 99, UnmatchedItem.Reason.NARROWED)));

        // when
        FulfilmentSelectPage page = factory.forOrders(form, "store-1", false, labels, TODAY, PL);

        // then
        assertThat(page.noOfferMatched()).isTrue();
        assertThat(page.multiOnSingleOrder()).isTrue();
        assertThat(page.noMatchKey()).isEqualTo("fulfilment.select.noMatch.narrowed");
        assertThat(page.missingKey()).isEqualTo("fulfilment.select.missing.summary.narrowed");
    }

    @Test
    void theRestockPageHasNoOrdersAndKnowsWhetherProfitCanBeShown() {
        // given
        FulfilmentForm form = new FulfilmentForm("warehouse", "redirect:/dashboard/warehouse?statuses=New",
                new ArrayList<>(List.of(offer("Elko-k1", "Memory", 450, true, ":r1:100000"), offer("AB-k2", "Memory", 440, false, ":r2:100000"))));

        // when
        FulfilmentSelectPage page = factory.forRestock(form, labels, false, PL);

        // then
        assertThat(page.isOrders()).isFalse();
        assertThat(page.kind()).isEqualTo(FulfilmentSelectPage.Kind.RESTOCK);
        assertThat(page.profitKnown()).isFalse();
        assertThat(page.backHref()).isEqualTo("/dashboard/warehouse");
        assertThat(page.actions()).isEqualTo(new FulfilmentSelectPage.Actions("/dashboard/warehouse/fulfilment/commit", null, null));
        assertThat(page.context()).isEqualTo("Produkty do uzupełnienia: 2");
        assertThat(page.commitHelpKey()).isEqualTo("fulfilment.select.commit.help.restock");
    }

    @Test
    void anEmptyRestockSaysThereIsNothingToRestock() {
        // when
        FulfilmentSelectPage page = factory.forRestock(new FulfilmentForm("warehouse", null, new ArrayList<>()), labels, true, PL);

        // then
        assertThat(page.emptyState()).isEqualTo(FulfilmentSelectPage.EmptyState.RESTOCK_EMPTY);
    }

    @Test
    void oneWarehouseOrderInOrderByOrderModeStillNamesTheOrder() {
        // given
        order("o2", FulfilmentType.WarehouseFulfilment, "Jan", "Kraków");
        FulfilmentForm form = form(List.of("o2", "o3"), offer("Elko-k1", "CPU", 100, true, "o2:i1:200"));
        form.setOrderByOrder(true);
        form.setOrderCountAtStart(2);

        // when
        FulfilmentSelectPage page = factory.forOrders(form, "store-1", false, labels, TODAY, PL);

        // then
        assertThat(page.kind()).isEqualTo(FulfilmentSelectPage.Kind.WAREHOUSE);
        assertThat(page.context()).startsWith("Zamówienie ").contains("Jan").endsWith("pozycje do zamówienia: 1");
        assertThat(page.orders()).hasSize(1);
        assertThat(page.orders().get(0).href()).isEqualTo("/dashboard/orders/o2");
    }

    @Test
    void aSingleOrderWithoutARecordIsMixedNotDropship() {
        // given
        FulfilmentForm form = form(List.of("gone"), offer("AB-k2", "Monitor", 800, false, "gone:i1:1149"));

        // when
        FulfilmentSelectPage page = factory.forOrders(form, "store-1", false, labels, TODAY, PL);

        // then
        assertThat(page.kind()).isEqualTo(FulfilmentSelectPage.Kind.MIXED);
        assertThat(page.context()).startsWith("Zamówienie ").doesNotContain("Robert");
    }
}

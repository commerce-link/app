package pl.commercelink.orders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFilterField;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OrderFilterCondition;
import pl.commercelink.orders.filters.services.ListOrderFiltersView;
import pl.commercelink.orders.filters.services.OrderFiltersService;
import pl.commercelink.web.orders.OrderListQuery;
import pl.commercelink.web.orders.OrdersPageModel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderListServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);
    private static final FilterActor ACTOR = new FilterActor("store-1", "u1", false);
    private static final Locale PL = new Locale("pl");

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderFiltersService orderFilters;

    private OrderListService service;
    private final List<Order> orders = new ArrayList<>();

    @BeforeEach
    void service() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        service = new OrderListService(ordersRepository, orderFilters, messages);
        when(ordersRepository.findByStore("store-1")).thenReturn(orders);
        when(orderFilters.list(ACTOR)).thenReturn(new ListOrderFiltersView(List.of(), List.of()));
    }

    private Order add(String id, OrderStatus status, LocalDate due, double total, double paid, String marketplace) {
        Order order = new Order("store-1");
        order.setOrderId(id);
        order.setStatus(status);
        order.setOrderedAt(LocalDateTime.of(2026, 9, 20, 12, 0).plusMinutes(orders.size()));
        order.setEstimatedShippingAt(due);
        order.setTotalPrice(total);
        Payment payment = new Payment(PaymentSource.BankTransfer);
        payment.setAmount(paid);
        order.setPayments(new ArrayList<>(List.of(payment)));
        order.setSource(marketplace == null ? new OrderSource(null, OrderSourceType.WebStore) : new OrderSource(marketplace, OrderSourceType.Marketplace));
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Jan");
        shipping.setSurname("Kowalski-" + id);
        order.setShippingDetails(shipping);
        orders.add(order);
        return order;
    }

    private static OrderListQuery query(String... keyValues) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.add(keyValues[i], keyValues[i + 1]);
        }
        return OrderListQuery.parse(map);
    }

    private OrdersPageModel page(OrderListQuery query) {
        return service.page(ACTOR, query, TODAY, PL);
    }

    @Test
    void defaultViewIsOpenOrdersDueFirstWithUndatedLast() {
        add("late", OrderStatus.New, TODAY.minusDays(2), 10, 10, null);
        add("none", OrderStatus.New, null, 10, 10, null);
        add("soon", OrderStatus.Assembly, TODAY.plusDays(1), 10, 10, null);
        add("today", OrderStatus.Blocked, TODAY, 10, 10, null);
        add("done", OrderStatus.Completed, TODAY.minusDays(9), 10, 10, null);

        OrdersPageModel model = page(query());

        assertThat(model.rows()).extracting(r -> r.href()).containsExactly(
                "/dashboard/orders/late", "/dashboard/orders/today", "/dashboard/orders/soon", "/dashboard/orders/none");
        assertThat(model.resultsLine()).isEqualTo("Wyniki: 4");
        assertThat(page(query("sort", "due", "dir", "desc")).rows()).extracting(r -> r.href()).containsExactly(
                "/dashboard/orders/soon", "/dashboard/orders/today", "/dashboard/orders/late", "/dashboard/orders/none");
    }

    @Test
    void emptyStateOffersHistoryWhenStoreHasOnlyClosedOrders() {
        add("done", OrderStatus.Completed, TODAY.minusDays(1), 10, 10, null);

        OrdersPageModel model = page(query());

        assertThat(model.emptyState().text()).isEqualTo("Brak otwartych zamówień.");
        assertThat(model.emptyState().actionLabel()).isEqualTo("Zobacz zakończone");
        assertThat(model.emptyState().actionHref()).isEqualTo("/dashboard/orders?status=Completed");
    }

    @Test
    void saveViewConditionsCarryHumanLabels() {
        OrderFilter filter = OrderFilter.of("Custom", List.of(
                OrderFilterCondition.of(OrderFilterField.ShipmentType, "courier"),
                OrderFilterCondition.of(OrderFilterField.SourceName, "Allegro"),
                OrderFilterCondition.of(OrderFilterField.ShippingPostalCode, "30-")));
        when(orderFilters.list(ACTOR)).thenReturn(new ListOrderFiltersView(List.of(), List.of(filter)));

        OrdersPageModel model = page(query("status", "New", "filterId", filter.getId()));

        assertThat(model.saveViewConditions()).extracting(c -> c.label()).containsExactly(
                "Otwieraj na statusie: Nowe",
                "Sposób dostawy: Kurier",
                "Marketplace: Allegro",
                "Kod pocztowy zaczyna się od: 30-");

        OrderFilter unknownValue = OrderFilter.of("Unknown", List.of(OrderFilterCondition.of(OrderFilterField.PaymentSource, "zzz")));
        when(orderFilters.list(ACTOR)).thenReturn(new ListOrderFiltersView(List.of(), List.of(unknownValue)));

        OrdersPageModel fallbackModel = page(query("filterId", unknownValue.getId()));

        assertThat(fallbackModel.saveViewConditions()).extracting(c -> c.label()).containsExactly("Płatność: zzz");
    }

    @Test
    void tilesCountTheWholeStoreWhileSegmentsCountWithinTheFilter() {
        add("a", OrderStatus.New, TODAY.minusDays(1), 100, 0, "Allegro");
        add("b", OrderStatus.New, TODAY, 100, 100, null);
        add("c", OrderStatus.Blocked, null, 50, 50, "Allegro");
        add("d", OrderStatus.Completed, null, 50, 0, "Allegro");
        OrderFilter allegro = OrderFilter.of("Allegro", List.of(OrderFilterCondition.of(OrderFilterField.SourceName, "Allegro")));
        when(orderFilters.list(ACTOR)).thenReturn(new ListOrderFiltersView(List.of(allegro), List.of(), Optional.of(allegro)));

        OrdersPageModel model = page(query("filterId", allegro.getId()));

        assertThat(model.tiles()).extracting(t -> t.count()).containsExactly(1L, 1L, 3L, 1L); // overdue, today, decide, unpaid (open only)
        assertThat(model.tiles().get(3).hint()).isEqualTo("z brakującą wpłatą");
        assertThat(model.tiles().get(3).valueOf()).isEqualTo("100,00 PLN");
        assertThat(model.tiles().get(2).hint()).isEqualTo("Nowe 2 · Zablokowane 1");
        assertThat(model.openSegments().get(0).count()).isEqualTo(2);   // all open within Allegro
        assertThat(segment(model, "Nowe").count()).isEqualTo(1);
        assertThat(segment(model, "Zablokowane").count()).isEqualTo(1);
        assertThat(model.historySegments()).extracting(s -> s.count()).containsExactly(1L, 0L);
        assertThat(model.rows()).hasSize(2);
        assertThat(model.chips()).extracting(c -> c.label()).containsExactly("Filtr: Allegro ★");
        assertThat(model.activeFilterStarred()).isTrue();
    }

    @Test
    void focusNarrowsWithinStatusAndFilterAndIsDisabledInHistory() {
        add("a", OrderStatus.New, TODAY.minusDays(1), 100, 100, null);
        add("b", OrderStatus.Assembly, TODAY.minusDays(1), 100, 100, null);
        add("c", OrderStatus.New, TODAY, 100, 100, null);

        OrdersPageModel model = page(query("focus", "overdue", "status", "New"));
        assertThat(model.rows()).extracting(r -> r.href()).containsExactly("/dashboard/orders/a");
        assertThat(model.tiles().get(0).pressed()).isTrue();
        assertThat(model.tiles().get(0).href()).isEqualTo("/dashboard/orders?status=New");
        assertThat(model.tiles().get(1).href()).isEqualTo("/dashboard/orders?status=New&focus=today");
        assertThat(model.chips()).extracting(c -> c.label()).containsExactly("Po terminie: 1");
        assertThat(model.chips().get(0).clearHref()).isEqualTo("/dashboard/orders?status=New");

        OrdersPageModel history = page(query("status", "Completed", "focus", "overdue"));
        assertThat(history.tiles()).allSatisfy(t -> assertThat(t.enabled()).isFalse());
        assertThat(history.chips()).isEmpty();
    }

    @Test
    void searchReachesHistoryAndCountsIt() {
        add("open-1", OrderStatus.New, null, 10, 10, null).getShippingDetails().setSurname("Nowak");
        add("open-2", OrderStatus.New, null, 10, 10, null);
        add("old-1", OrderStatus.Completed, null, 10, 10, null).getShippingDetails().setSurname("Nowak");
        add("old-2", OrderStatus.Cancelled, null, 10, 10, null).getShippingDetails().setSurname("Nowak");

        OrdersPageModel model = page(query("q", "nowak"));

        assertThat(model.rows()).extracting(r -> r.href()).containsExactly("/dashboard/orders/open-1");
        assertThat(model.resultsLine()).isEqualTo("Wyniki dla „nowak”: 1 · w historii: 2");
        assertThat(model.historySegments()).extracting(s -> s.count()).containsExactly(1L, 1L);
        assertThat(model.chips()).extracting(c -> c.label()).containsExactly("Szukasz: „nowak”");
    }

    @Test
    void sortsByAmountNumberAndOrderedAndHistoryIsNewestFirst() {
        add("b", OrderStatus.New, null, 300, 300, null);
        add("a", OrderStatus.New, null, 100, 100, null);
        add("c", OrderStatus.New, null, 200, 200, null);
        add("h1", OrderStatus.Completed, null, 1, 1, null);
        add("h2", OrderStatus.Completed, null, 1, 1, null);

        assertThat(page(query("sort", "amount")).rows()).extracting(r -> r.href()).containsExactly("/dashboard/orders/a", "/dashboard/orders/c", "/dashboard/orders/b");
        assertThat(page(query("sort", "amount", "dir", "desc")).rows()).extracting(r -> r.href()).containsExactly("/dashboard/orders/b", "/dashboard/orders/c", "/dashboard/orders/a");
        assertThat(page(query("sort", "number")).rows()).extracting(r -> r.href()).containsExactly("/dashboard/orders/a", "/dashboard/orders/b", "/dashboard/orders/c");
        assertThat(page(query("status", "Completed")).rows()).extracting(r -> r.href()).containsExactly("/dashboard/orders/h2", "/dashboard/orders/h1");
        assertThat(page(query("status", "Completed")).resultsLine()).isEqualTo("Wyniki: 2 · najnowsze pierwsze");
        assertThat(page(query("sort", "amount")).sortHeaders().get(OrderListQuery.Sort.AMOUNT).ariaSort()).isEqualTo("ascending");
        assertThat(page(query("sort", "amount")).sortHeaders().get(OrderListQuery.Sort.AMOUNT).href()).isEqualTo("/dashboard/orders?sort=amount&dir=desc");
        assertThat(page(query()).sortHeaders().get(OrderListQuery.Sort.DUE).ariaSort()).isEqualTo("ascending");
        assertThat(page(query()).sortHeaders().get(OrderListQuery.Sort.NUMBER).ariaSort()).isEqualTo("none");
    }

    @Test
    void pagesFiftyRowsAndClampsThePage() {
        IntStream.range(0, 51).forEach(i -> add(String.format("o%02d", i), OrderStatus.New, null, i, i, null));

        OrdersPageModel first = page(query());
        assertThat(first.rows()).hasSize(50);
        assertThat(first.pagination().isNeeded()).isTrue();
        assertThat(first.pagination().nextHref()).isEqualTo("/dashboard/orders?page=2");

        OrdersPageModel beyond = page(query("page", "9"));
        assertThat(beyond.rows()).hasSize(1);
        assertThat(beyond.pagination().page()).isEqualTo(2);
        assertThat(beyond.pagination().previousHref()).isEqualTo("/dashboard/orders");
    }

    @Test
    void emptyStatesPickTheRightMessageAndAction() {
        assertThat(page(query()).emptyState().text()).isEqualTo("Nie masz jeszcze zamówień. Pojawią się tu ze sklepu, marketplace’ów i sprzedaży POS.");
        assertThat(page(query()).emptyState().actionHref()).isNull();
        add("a", OrderStatus.New, TODAY.plusDays(1), 10, 10, null);
        assertThat(page(query("focus", "overdue")).emptyState().text()).isEqualTo("Nic po terminie.");
        assertThat(page(query("focus", "overdue")).emptyState().actionHref()).isEqualTo("/dashboard/orders");
        assertThat(page(query("q", "zzz")).emptyState().text()).startsWith("Brak wyników dla „zzz”");
        assertThat(page(query("q", "zzz")).emptyState().actionHref()).isEqualTo("/dashboard/orders");
        assertThat(page(query("status", "Blocked")).emptyState().text()).isEqualTo("Brak zamówień w statusie „Zablokowane”.");
        OrderFilter f = OrderFilter.of("Kurier", List.of(OrderFilterCondition.of(OrderFilterField.ShipmentType, "PickupPoint")));
        when(orderFilters.list(ACTOR)).thenReturn(new ListOrderFiltersView(List.of(), List.of(f)));
        assertThat(page(query("filterId", f.getId())).emptyState().text()).isEqualTo("Ten filtr nie ma dziś zamówień.");
        assertThat(page(query("filterId", f.getId())).emptyState().actionHref()).isEqualTo("/dashboard/orders?filterId=");
        assertThat(page(query()).emptyState()).isNull();
    }

    @Test
    void unknownFilterIdIsIgnoredAndFilterOptionsCarryTheStar() {
        add("a", OrderStatus.New, null, 10, 10, null);
        OrderFilter own = OrderFilter.of("Mój", List.of(OrderFilterCondition.of(OrderFilterField.Status, "New")));
        OrderFilter shared = OrderFilter.of("Sklepowy", List.of(OrderFilterCondition.of(OrderFilterField.Status, "Blocked")));
        when(orderFilters.list(ACTOR)).thenReturn(new ListOrderFiltersView(List.of(shared), List.of(own), Optional.of(own)));

        OrdersPageModel model = page(query("filterId", "does-not-exist"));

        assertThat(model.rows()).hasSize(1);
        assertThat(model.chips()).isEmpty();
        assertThat(model.activeFilter()).isEmpty();
        assertThat(model.filterOptions()).extracting(o -> o.label()).containsExactly("Sklepowy", "★ Mój");
        assertThat(model.filterOptions()).extracting(o -> o.shared()).containsExactly(true, false);
        assertThat(model.saveViewConditions()).isEmpty();
        // the segment's status replaces the filter's own Status condition; the filter's other conditions are kept
        OrdersPageModel narrowed = page(query("status", "New", "filterId", shared.getId()));
        assertThat(narrowed.saveViewConditions()).extracting(c -> c.field()).containsExactly("Status");
        assertThat(narrowed.saveViewConditions().get(0).value()).isEqualTo("New");
    }

    private static OrdersPageModel.Segment segment(OrdersPageModel model, String label) {
        return model.openSegments().stream().filter(s -> s.label().equals(label)).findFirst().orElseThrow();
    }
}

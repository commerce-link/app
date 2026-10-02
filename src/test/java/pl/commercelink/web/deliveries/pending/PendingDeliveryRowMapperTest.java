package pl.commercelink.web.deliveries.pending;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.orders.*;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.web.deliveries.pending.PendingDeliveriesQuery.Kind;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PendingDeliveryRowMapperTest {

    static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    static final String ORDER_A = "5a7c3e10-1111-4111-8111-111111111111";
    static final String ORDER_B = "c47d2a90-2222-4222-8222-222222222222";
    static final String ORDER_C = "1de57483-fb1d-3dc4-971e-0822cc4e537f";

    @Mock
    private SupplierLabelMap labels;
    private ResourceBundleMessageSource messages;

    @BeforeEach
    void setUp() {
        messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        lenient().when(labels.of(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    private PendingDeliveryRowMapper mapper(boolean superAdmin, Map<String, Order> orders, Set<String> approvalProviders) {
        return new PendingDeliveryRowMapper(messages, new Locale("pl"), labels, orders, approvalProviders, "store-1", superAdmin);
    }

    static Order order(String orderId, String name, String email, LocalDate shippingAt, boolean directToConsumer) {
        Order order = new Order();
        order.setOrderId(orderId);
        BillingDetails billing = new BillingDetails();
        billing.setName(name);
        billing.setEmail(email);
        order.setBillingDetails(billing);
        order.setEstimatedShippingAt(shippingAt);
        order.setFulfilmentType(directToConsumer ? FulfilmentType.DirectToConsumer : FulfilmentType.WarehouseFulfilment);
        return order;
    }

    static Allocation fromOrder(Order order, String itemId, String name, String mfn, int qty, double cost, String provider) {
        OrderItem item = new OrderItem(order.getOrderId(), "Category", name, qty, cost * 1.23, null, false);
        item.setItemId(itemId);
        item.setEan("590" + mfn.hashCode());
        item.setManufacturerCode(mfn);
        item.setCost(cost);
        item.setDeliveryId(provider);
        item.setStatus(FulfilmentStatus.Allocation);
        return Allocation.fromOrderItem(order, item);
    }

    static Allocation fromWarehouse(String itemId, String name, String mfn, int qty, double cost, String provider) {
        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(null, itemId, "Warehouse"));
        allocation.setType(AllocationType.Warehouse);
        allocation.setName(name);
        allocation.setMfn(mfn);
        allocation.setEan("590" + mfn.hashCode());
        allocation.setQty(qty);
        allocation.setUnitCost(cost);
        allocation.setDeliveryId(provider);
        return allocation;
    }

    static Delivery warehouseDelivery(String provider, List<Allocation> allocations) {
        Delivery delivery = new Delivery("store-1", null, provider);
        delivery.setType(DeliveryType.WAREHOUSE);
        delivery.setAllocations(allocations);
        delivery.setItems(DeliveryItem.groupAndUnify(allocations));
        return delivery;
    }

    static DropshipCandidate candidate(Order order, String provider, List<Allocation> allocations) {
        return new DropshipCandidate(order.getOrderId(), provider, DeliveryItem.groupAndUnify(allocations), allocations);
    }

    @Test
    void warehouseRowSumsPiecesCostAndSourcesAndTakesTheEarliestDueDate() {
        // given
        Order a = order(ORDER_A, "Jan Nowak", "jan@example.com", TODAY.minusDays(1), false);
        Order b = order(ORDER_B, "Anna Kowal", "anna@example.com", TODAY.plusDays(2), false);
        Delivery delivery = warehouseDelivery("AcmeB", List.of(
                fromOrder(a, "1", "Samsung MirageDrive 2TB NVMe", "MFN-MIRAGE-01", 1, 635.0, "AcmeB"),
                fromWarehouse("w-1", "Samsung MirageDrive 2TB NVMe", "MFN-MIRAGE-01", 2, 635.0, "AcmeB"),
                fromOrder(b, "2", "Logitech MX Keys S", "MFN-MXKEYS", 1, 389.0, "AcmeB")));

        // when
        PendingDeliveryRow row = mapper(false, Map.of(ORDER_A, a, ORDER_B, b), Set.of())
                .warehouse(delivery, TODAY);

        // then
        assertThat(row.kind()).isEqualTo(Kind.WAREHOUSE);
        assertThat(row.key()).isEqualTo("AcmeB");
        assertThat(row.keyHref()).isNull();
        assertThat(row.pieces()).isEqualTo(4);
        assertThat(row.piecesText()).isEqualTo("4 szt.");
        assertThat(row.costText()).isEqualTo("2 294,00 PLN");
        assertThat(row.sourceText()).isEqualTo("Zamówienia: 2 + uzupełnienie magazynu");
        assertThat(row.due()).isEqualTo(TODAY.minusDays(1));
        assertThat(row.dueNote()).isEqualTo("po terminie: 1 dzień");
        assertThat(row.dueTone()).isEqualTo("is-bad");
        assertThat(row.approval()).isFalse();
        assertThat(row.createHref()).isEqualTo("/dashboard/deliveries/create/AcmeB");
        assertThat(row.items()).extracting(PendingDeliveryRow.Item::name)
                .containsExactly("Logitech MX Keys S", "Samsung MirageDrive 2TB NVMe");
        PendingDeliveryRow.Item samsung = row.items().get(1);
        assertThat(samsung.qtyText()).isEqualTo("3 szt.");
        assertThat(samsung.valueText()).isEqualTo("1 905,00 PLN");
        assertThat(samsung.sources()).containsExactly(
                new PendingDeliveryRow.Source("5a7c3e10", "/dashboard/orders/" + ORDER_A, 1, false),
                new PendingDeliveryRow.Source("Magazyn", "/dashboard/warehouse/items/w-1", 2, true));
    }

    @Test
    void dropshipRowLinksTheOrderAndItsDropshipPage() {
        // given
        Order c = order(ORDER_C, "Barbara Zając", "barbara@example.com", TODAY, true);
        DropshipCandidate candidate = candidate(c, "AcmeB",
                List.of(fromOrder(c, "3", "Samsung MirageDrive 2TB NVMe", "MFN-MIRAGE-01", 1, 635.0, "AcmeB")));

        // when
        PendingDeliveryRow row = mapper(false, Map.of(ORDER_C, c), Set.of("AcmeB"))
                .dropship(candidate, TODAY);

        // then
        assertThat(row.kind()).isEqualTo(Kind.DROPSHIP);
        assertThat(row.key()).isEqualTo("1de57483");
        assertThat(row.keyHref()).isEqualTo("/dashboard/orders/" + ORDER_C);
        assertThat(row.customer()).isEqualTo("Barbara Zając");
        assertThat(row.createHref()).isEqualTo("/dashboard/orders/" + ORDER_C + "/dropship?provider=AcmeB");
        assertThat(row.dueNote()).isEqualTo("dziś");
        assertThat(row.dueTone()).isEqualTo("is-warn");
        assertThat(row.approval()).isTrue();
        assertThat(row.forwardToCustomer()).isFalse();
        assertThat(row.label()).isEqualTo("1de57483");
    }

    @Test
    void superAdminLinksAreStoreScopedAndTheWarehouseSourceHasNoLink() {
        // given
        Order a = order(ORDER_A, "Jan Nowak", "jan@example.com", TODAY, false);
        Delivery delivery = warehouseDelivery("Acme", List.of(
                fromOrder(a, "1", "Produkt", "MFN-1", 1, 10.0, "Acme"),
                fromWarehouse("w-9", "Produkt", "MFN-1", 1, 10.0, "Acme")));
        Order c = order(ORDER_C, "Barbara Zając", "barbara@example.com", TODAY, true);
        DropshipCandidate candidate = candidate(c, "Acme", List.of(fromOrder(c, "3", "Produkt", "MFN-1", 1, 10.0, "Acme")));
        PendingDeliveryRowMapper mapper = mapper(true, Map.of(ORDER_A, a, ORDER_C, c), Set.of());

        // when
        PendingDeliveryRow warehouse = mapper.warehouse(delivery, TODAY);
        PendingDeliveryRow dropship = mapper.dropship(candidate, TODAY);

        // then
        assertThat(warehouse.createHref()).isEqualTo("/dashboard/store/store-1/deliveries/create/Acme");
        assertThat(warehouse.items().getFirst().sources()).containsExactly(
                new PendingDeliveryRow.Source("5a7c3e10", "/dashboard/store/store-1/orders/" + ORDER_A, 1, false),
                new PendingDeliveryRow.Source("Magazyn", null, 1, true));
        assertThat(warehouse.approval()).isFalse();
        assertThat(dropship.keyHref()).isEqualTo("/dashboard/store/store-1/orders/" + ORDER_C);
        assertThat(dropship.createHref()).isEqualTo("/dashboard/store/store-1/orders/" + ORDER_C + "/dropship?provider=Acme");
    }

    @Test
    void approvalComesFromTheSuppliersMarkedForApproval() {
        // given
        Order c = order(ORDER_C, "Barbara Zając", "barbara@example.com", TODAY, true);
        Order a = order(ORDER_A, "Jan Nowak", "jan@example.com", TODAY, false);
        PendingDeliveryRowMapper mapper = mapper(false, Map.of(ORDER_A, a, ORDER_C, c), Set.of("Global"));
        Delivery global = warehouseDelivery("Global", List.of(fromOrder(a, "1", "Produkt", "MFN-1", 1, 10.0, "Global")));
        Delivery own = warehouseDelivery("Own", List.of(fromOrder(a, "2", "Produkt", "MFN-1", 1, 10.0, "Own")));
        DropshipCandidate dropship = candidate(c, "Global", List.of(fromOrder(c, "3", "Produkt", "MFN-1", 1, 10.0, "Global")));

        // when
        boolean globalWarehouse = mapper.warehouse(global, TODAY).approval();
        boolean ownWarehouse = mapper.warehouse(own, TODAY).approval();
        boolean globalDropship = mapper.dropship(dropship, TODAY).approval();

        // then
        assertThat(globalWarehouse).isTrue();
        assertThat(ownWarehouse).isFalse();
        assertThat(globalDropship).isTrue();
    }

    @Test
    void dueNotesCoverEveryDistance() {
        // given
        Order tomorrow = order(ORDER_A, "A", "a@example.com", TODAY.plusDays(1), true);
        Order later = order(ORDER_B, "B", "b@example.com", LocalDate.of(2026, 10, 6), true);
        Order nextYear = order(ORDER_C, "C", "c@example.com", LocalDate.of(2027, 1, 5), true);
        Order overdue = order("9e2b7c41-0000-4000-8000-000000000000", "D", "d@example.com", TODAY.minusDays(3), true);
        Map<String, Order> orders = Map.of(ORDER_A, tomorrow, ORDER_B, later, ORDER_C, nextYear, overdue.getOrderId(), overdue);
        PendingDeliveryRowMapper mapper = mapper(false, orders, Set.of());

        // when / then
        assertThat(mapper.dropship(candidate(tomorrow, "Acme", List.of(fromOrder(tomorrow, "1", "P", "M", 1, 1.0, "Acme"))), TODAY).dueNote()).isEqualTo("jutro");
        assertThat(mapper.dropship(candidate(later, "Acme", List.of(fromOrder(later, "2", "P", "M", 1, 1.0, "Acme"))), TODAY).dueNote()).isEqualTo("termin 06.10");
        assertThat(mapper.dropship(candidate(nextYear, "Acme", List.of(fromOrder(nextYear, "3", "P", "M", 1, 1.0, "Acme"))), TODAY).dueNote()).isEqualTo("termin 05.01.2027");
        PendingDeliveryRow late = mapper.dropship(candidate(overdue, "Acme", List.of(fromOrder(overdue, "4", "P", "M", 1, 1.0, "Acme"))), TODAY);
        assertThat(late.dueNote()).isEqualTo("po terminie: 3 dni");
        assertThat(late.dueTone()).isEqualTo("is-bad");
        PendingDeliveryRow restock = mapper.warehouse(warehouseDelivery("Kosatec", List.of(fromWarehouse("w-1", "PSU", "BN343", 4, 402.5, "Kosatec"))), TODAY);
        assertThat(restock.due()).isNull();
        assertThat(restock.dueNote()).isEqualTo("bez terminu");
        assertThat(restock.dueTone()).isEqualTo("is-none");
        assertThat(restock.sourceText()).isEqualTo("uzupełnienie magazynu");
    }

    @Test
    void createHrefAndDetailIdSurviveAnUnusualProviderIdentity() {
        // given
        String provider = "Acme B#2/ł";
        Order c = order(ORDER_C, "C", "c@example.com", TODAY, true);
        PendingDeliveryRowMapper mapper = mapper(false, Map.of(ORDER_C, c), Set.of());

        // when
        PendingDeliveryRow warehouse = mapper.warehouse(warehouseDelivery(provider, List.of(fromWarehouse("w-1", "P", "M", 1, 1.0, provider))), TODAY);
        PendingDeliveryRow dropship = mapper.dropship(candidate(c, provider, List.of(fromOrder(c, "1", "P", "M", 1, 1.0, provider))), TODAY);

        // then
        assertThat(warehouse.createHref()).isEqualTo("/dashboard/deliveries/create/Acme%20B%232%2F%C5%82");
        assertThat(dropship.createHref()).endsWith("/dropship?provider=Acme+B%232%2F%C5%82");
        assertThat(warehouse.detailId()).matches("[A-Za-z][A-Za-z0-9_-]*");
        assertThat(dropship.detailId()).matches("[A-Za-z][A-Za-z0-9_-]*").isNotEqualTo(warehouse.detailId());
    }

    @Test
    void anOrderMissingFromTheMapGivesARowWithoutDueDateAndCustomer() {
        // given
        Order c = order(ORDER_C, "C", "c@example.com", TODAY, true);
        DropshipCandidate candidate = candidate(c, "Acme", List.of(fromOrder(c, "1", "P", "M", 1, 1.0, "Acme")));

        // when
        PendingDeliveryRow row = mapper(false, Map.of(), Set.of()).dropship(candidate, TODAY);

        // then
        assertThat(row.customer()).isNull();
        assertThat(row.due()).isNull();
        assertThat(row.dueNote()).isEqualTo("bez terminu");
        assertThat(row.key()).isEqualTo("1de57483");
    }

    @Test
    void customerFallsBackToTheEmailWhenTheNameIsBlank() {
        // given
        Order c = order(ORDER_C, " ", "barbara@example.com", TODAY, true);

        // when
        PendingDeliveryRow row = mapper(false, Map.of(ORDER_C, c), Set.of())
                .dropship(candidate(c, "Acme", List.of(fromOrder(c, "1", "P", "M", 1, 1.0, "Acme"))), TODAY);

        // then
        assertThat(row.customer()).isEqualTo("barbara@example.com");
    }

    @Test
    void searchIsLiteralAndCaseInsensitive() {
        // given
        Order c = order(ORDER_C, "Barbara Zając", "barbara@example.com", TODAY, true);
        PendingDeliveryRow row = mapper(false, Map.of(ORDER_C, c), Set.of())
                .dropship(candidate(c, "AcmeB", List.of(fromOrder(c, "1", "Samsung MirageDrive", "MFN-MIRAGE-01", 1, 1.0, "AcmeB"))), TODAY);

        // when / then
        assertThat(row.matches("ZAJĄC")).isTrue();
        assertThat(row.matches("1DE5")).isTrue();
        assertThat(row.matches("e57483")).isFalse();
        assertThat(row.matches("mfn-mirage")).isTrue();
        assertThat(row.matches("barbara@EXAMPLE")).isTrue();
        assertThat(row.matches("a+b")).isFalse();
        assertThat(row.matches("(")).isFalse();
        assertThat(row.matches(null)).isTrue();
    }

    @Test
    void aWarehouseDeliveryWithAForwardedOrderIsMarked() {
        // given
        Order dtc = order(ORDER_A, "Jan Nowak", "jan@example.com", TODAY, true);

        // when
        PendingDeliveryRow row = mapper(false, Map.of(ORDER_A, dtc), Set.of())
                .warehouse(warehouseDelivery("Kosatec", List.of(fromOrder(dtc, "1", "P", "M", 1, 1.0, "Kosatec"))), TODAY);

        // then
        assertThat(row.forwardToCustomer()).isTrue();
    }
}

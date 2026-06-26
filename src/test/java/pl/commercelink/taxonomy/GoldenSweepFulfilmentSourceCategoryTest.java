package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentGroupsGenerator;
import pl.commercelink.orders.fulfilment.FulfilmentItem;
import pl.commercelink.orders.fulfilment.FulfilmentSource;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SWEEP surface D — category participates in {@code FulfilmentSource} identity, and the
 * Services-category bypass on the fulfilment-eligibility filter.
 *
 * Sites:
 *  - FulfilmentSource.equals()/hashCode(): {@code category} is part of the equality contract
 *    (two sources differing only in category are NOT equal; hashCode differs in practice).
 *    Exercised on REAL FulfilmentSource objects (real equals()/hashCode()).
 *  - FulfilmentGroupsGenerator.run() eligibility gates (REAL call-site, driven end-to-end with a
 *    mocked InventoryView; the two category-bearing predicates live inside run()):
 *        c -> ProductCategory.Services == c.getSource().getCategory() || isNotBlank(ean)
 *        c -> ProductCategory.Services == c.getSource().getCategory() || isNotBlank(mfn)
 *    Services-category sources bypass the "must have ean AND mfn" gate; non-Services need both.
 */
@ExtendWith(MockitoExtension.class)
class GoldenSweepFulfilmentSourceCategoryTest {

    private static FulfilmentSource source(ProductCategory category, String ean, String mfn) {
        FulfilmentSource s = new FulfilmentSource();
        s.setSequenceNumber(category.ordinal());
        s.setItemType(CategorySnapshot.typeOfKey(category.name()));
        s.setProvider("Action");
        s.setEan(ean);
        s.setMfn(mfn);
        s.setQty(1);
        s.setPriceNet(10);
        s.setPriceGross(12);
        return s;
    }

    // ---- helpers driving the REAL FulfilmentGroupsGenerator.run() eligibility gates ----

    private static OrderItem productOrderItem(ProductCategory category, String sku) {
        // non-Services category with a SKU -> run() takes the inventory-lookup path.
        return new OrderItem(null, category.name(), "name", 1, 0, sku, false);
    }

    private static OrderItem servicesOrderItem() {
        // Services category -> run()/createFulfilments short-circuits to an internal warehouse item.
        return new OrderItem(null, ProductCategory.Services.name(), "service", 1, 0, null, false);
    }

    private static InventoryItem offer(String ean, String mfn) {
        // "Action" is an accepted (non-OTHER) provider so hasProvider() passes; gates then decide.
        return new InventoryItem(ean, mfn, 10, "PLN", 1, 1, "Action", true, false, false);
    }

    /** Runs the REAL run() against a mocked inventory that returns {@code offers} for the item's SKU. */
    private static List<Integer> runEligibilityGates(OrderItem orderItem, List<InventoryItem> offers) {
        InventoryView inventory = mock(InventoryView.class);
        if (orderItem.hasSKU()) {
            MatchedInventory matched = mock(MatchedInventory.class);
            when(matched.getInventoryItems()).thenReturn(offers);
            when(inventory.findByProductCode(orderItem.getSku())).thenReturn(matched);
        }
        FulfilmentGroupsGenerator generator = FulfilmentGroupsGenerator.builder()
                .withInventory(inventory)
                .build();
        return generator.run(List.of(orderItem)).stream()
                .map(FulfilmentItem::getSource)
                .map(FulfilmentSource::getSequenceNumber)
                .collect(Collectors.toList());
    }

    @Test
    void fulfilmentSourcesDifferingOnlyInCategoryAreNotEqual() {
        // given
        FulfilmentSource cpu = source(ProductCategory.CPU, "111", "M");
        FulfilmentSource laptops = source(ProductCategory.Laptops, "111", "M");
        FulfilmentSource cpuTwin = source(ProductCategory.CPU, "111", "M");

        // then
        // category is part of equals(); same fields but different category => not equal.
        assertThat(cpu).isNotEqualTo(laptops);
        // identical fields including category => equal, and hashCode agrees.
        assertThat(cpu).isEqualTo(cpuTwin);
        assertThat(cpu.hashCode()).isEqualTo(cpuTwin.hashCode());
    }

    @Test
    void fulfilmentSourceHashCodeReflectsCategory() {
        // given
        FulfilmentSource cpu = source(ProductCategory.CPU, "111", "M");
        FulfilmentSource laptops = source(ProductCategory.Laptops, "111", "M");

        // then
        // category feeds Objects.hash(...), so distinct categories yield distinct hashes here.
        assertThat(cpu.hashCode()).isNotEqualTo(laptops.hashCode());
    }

    @Test
    void servicesCategoryBypassesEanAndMfnRequirement() {
        // given
        // a Services order item resolves to an internal source with NO ean and NO mfn.
        OrderItem service = servicesOrderItem();

        // when (REAL run() eligibility gates)
        List<Integer> passing = runEligibilityGates(service, List.of());

        // then
        // Services short-circuits both gates to true despite missing ean/mfn -> it survives run().
        assertThat(passing).containsExactly(ProductCategory.Services.ordinal());
    }

    @Test
    void nonServicesCategoryRequiresEanAndMfn() {
        // given
        OrderItem withBoth = productOrderItem(ProductCategory.CPU, "M");
        OrderItem noEan = productOrderItem(ProductCategory.CPU, "M");
        OrderItem noMfn = productOrderItem(ProductCategory.CPU, "M");

        // when / then (REAL run() eligibility gates)
        // fully identified offer passes both gates.
        assertThat(runEligibilityGates(withBoth, List.of(offer("111", "M")))).containsExactly(ProductCategory.CPU.ordinal());
        // missing ean fails the ean gate (no Services bypass) -> rejected.
        assertThat(runEligibilityGates(noEan, List.of(offer(null, "M")))).isEmpty();
        // missing mfn fails the mfn gate (no Services bypass) -> rejected.
        assertThat(runEligibilityGates(noMfn, List.of(offer("111", null)))).isEmpty();
    }

    @Test
    void onlyServicesAndFullyIdentifiedNonServicesPassBothGates() {
        // given
        // four order items fed through the REAL run() one by one (run() collapses a Services item to an
        // internal warehouse source, and a SKU item to the matched inventory offers).
        List<List<Integer>> results = List.of(
                runEligibilityGates(servicesOrderItem(), List.of()),                                 // service, bypasses
                runEligibilityGates(productOrderItem(ProductCategory.CPU, "M"), List.of(offer("111", "M"))), // fully identified
                runEligibilityGates(productOrderItem(ProductCategory.CPU, "M"), List.of(offer(null, "M"))),  // missing ean -> rejected
                runEligibilityGates(productOrderItem(ProductCategory.GPU, "M"), List.of(offer("222", null))) // missing mfn -> rejected
        );

        // when
        List<Integer> passing = results.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // then
        assertThat(passing).containsExactly(ProductCategory.Services.ordinal(), ProductCategory.CPU.ordinal());
    }
}

package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import pl.commercelink.inventory.deliveries.SupplierPurchaseService;
import pl.commercelink.inventory.supplier.api.SupplierOrderOption;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionChoice;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionsContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderOptionsModelTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "Acme";

    @Mock
    private SupplierPurchaseService supplierPurchaseService;

    private static SupplierOrderOption option(String key, String defaultValue) {
        return new SupplierOrderOption(key, key, List.of(new SupplierOrderOptionChoice("fast", "Fast", null),
                new SupplierOrderOptionChoice("slow", "Slow", null)), defaultValue, true);
    }

    @Test
    void fetchedOptionsArePreselectedWithTheirDefaultsWhenNothingWasPosted() {
        // given
        SupplierOrderOptionsContext context = SupplierOrderOptionsContext.warehouse();
        List<SupplierOrderOption> options = List.of(option("lane", "fast"), option("pay", null));
        when(supplierPurchaseService.orderOptions(eq(STORE_ID), eq(PROVIDER), same(context))).thenReturn(options);
        Model model = new ConcurrentModel();

        // when
        OrderOptionsModel.addOrderOptions(supplierPurchaseService, STORE_ID, PROVIDER, context, null, model);

        // then
        assertThat(model.getAttribute("orderOptions")).isEqualTo(options);
        assertThat(model.getAttribute("selectedOptions")).isEqualTo(Map.of("lane", "fast"));
        assertThat(model.containsAttribute("orderOptionsError")).isFalse();
    }

    @Test
    void aPostedChoiceWinsOverTheDefaultAndABlankOneFallsBackToIt() {
        // given
        SupplierOrderOptionsContext context = SupplierOrderOptionsContext.warehouse();
        when(supplierPurchaseService.orderOptions(eq(STORE_ID), eq(PROVIDER), same(context)))
                .thenReturn(List.of(option("lane", "fast"), option("pay", "fast")));
        Model model = new ConcurrentModel();

        // when
        OrderOptionsModel.addOrderOptions(supplierPurchaseService, STORE_ID, PROVIDER, context,
                Map.of("lane", "slow", "pay", " "), model);

        // then
        assertThat(model.getAttribute("selectedOptions")).isEqualTo(Map.of("lane", "slow", "pay", "fast"));
    }

    @Test
    void aFailedFetchLeavesNoOptionsAndTheReasonForTheBlockedOrder() {
        // given
        SupplierOrderOptionsContext context = SupplierOrderOptionsContext.warehouse();
        when(supplierPurchaseService.orderOptions(eq(STORE_ID), eq(PROVIDER), same(context)))
                .thenThrow(new RuntimeException("supplier unavailable"));
        Model model = new ConcurrentModel();

        // when
        OrderOptionsModel.addOrderOptions(supplierPurchaseService, STORE_ID, PROVIDER, context, Map.of("lane", "slow"), model);

        // then
        assertThat(model.getAttribute("orderOptionsError")).isEqualTo("supplier unavailable");
        assertThat((List<?>) model.getAttribute("orderOptions")).isEmpty();
        assertThat((Map<?, ?>) model.getAttribute("selectedOptions")).isEmpty();
    }
}

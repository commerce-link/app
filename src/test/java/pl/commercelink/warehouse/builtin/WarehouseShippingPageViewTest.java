package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.shipping.ShippingPageView;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseShippingPageViewTest {

    @Test
    void theBookingPageOfWarehouseItemsLeadsBackToTheWarehouseAndCountsTheItems() {
        // given
        ShippingForm form = new ShippingForm(null, "warehouse");
        form.setOrderItemIds(List.of("w-1", "w-2"));

        // when
        ShippingPageView view = new WarehouseShippingController().pageView(form);

        // then
        assertThat(view).isEqualTo(new ShippingPageView("/dashboard/warehouse", "nav.warehouse", null,
                "shipping.lead.warehouse", 2));
    }
}

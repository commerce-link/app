package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.shipping.ShippingPageView;

import static org.assertj.core.api.Assertions.assertThat;

class RMAShippingPageViewTest {

    @Test
    void theBookingPageOfAReturnLeadsBackToItAndNamesTheDirection() {
        // given
        ShippingForm toClient = new ShippingForm("rma-7", "rma");
        toClient.setToClient(true);
        ShippingForm toDistributor = new ShippingForm("rma-7", "rma");

        // when
        ShippingPageView clientView = new RMAShippingController().pageView(toClient);
        ShippingPageView distributorView = new RMAShippingController().pageView(toDistributor);

        // then
        assertThat(clientView).isEqualTo(new ShippingPageView("/dashboard/rma/rma-7", "rma.details", null,
                "shipping.lead.rma.client", "rma-7"));
        assertThat(distributorView.leadKey()).isEqualTo("shipping.lead.rma.center");
    }
}

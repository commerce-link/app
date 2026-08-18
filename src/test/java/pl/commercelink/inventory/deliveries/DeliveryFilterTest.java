package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryFilterTest {

    @Test
    void carriesTheAwaitingApprovalFlag() {
        // given
        DeliveryFilter filter = new DeliveryFilter(null, null, null, null, null, true, false, false, true, false);

        // when / then
        assertThat(filter.isAwaitingApproval()).isTrue();
    }

    @Test
    void defaultsToNotFilteringByApprovalState() {
        // given
        DeliveryFilter filter = new DeliveryFilter(null, null, null, null, null, true, false, false, false, false);

        // when / then
        assertThat(filter.isAwaitingApproval()).isFalse();
    }

    @Test
    void carriesTheGlobalOnlyFlag() {
        // given
        DeliveryFilter filter = new DeliveryFilter(null, null, null, null, null, true, false, false, false, true);

        // when / then
        assertThat(filter.isGlobalOnly()).isTrue();
    }
}

package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentCreationEventPublisherTest {

    @Test
    void checksWaitLongerWithEveryAttemptAndStayAtTheLongestDelay() {
        // when / then
        assertThat(ShipmentCreationEventPublisher.delayFor(1)).isEqualTo(3);
        assertThat(ShipmentCreationEventPublisher.delayFor(2)).isEqualTo(5);
        assertThat(ShipmentCreationEventPublisher.delayFor(8)).isEqualTo(60);
        assertThat(ShipmentCreationEventPublisher.delayFor(20)).isEqualTo(60);
        assertThat(ShipmentCreationEventPublisher.delayFor(0)).isEqualTo(3);
    }
}

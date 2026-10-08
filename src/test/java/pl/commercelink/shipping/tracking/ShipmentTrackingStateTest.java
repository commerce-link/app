package pl.commercelink.shipping.tracking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.shipping.tracking.ShipmentTrackingState.COLLECTED;
import static pl.commercelink.shipping.tracking.ShipmentTrackingState.DELIVERED;
import static pl.commercelink.shipping.tracking.ShipmentTrackingState.EXPIRED;

class ShipmentTrackingStateTest {

    @Test
    void aParcelWithoutStateMovesToAnyState() {
        assertThat(ShipmentTrackingState.isForward(null, COLLECTED)).isTrue();
        assertThat(ShipmentTrackingState.isForward(null, DELIVERED)).isTrue();
        assertThat(ShipmentTrackingState.isForward(null, EXPIRED)).isTrue();
    }

    @Test
    void collectedMovesOnlyToDeliveredOrExpired() {
        assertThat(ShipmentTrackingState.isForward(COLLECTED, COLLECTED)).isFalse();
        assertThat(ShipmentTrackingState.isForward(COLLECTED, DELIVERED)).isTrue();
        assertThat(ShipmentTrackingState.isForward(COLLECTED, EXPIRED)).isTrue();
    }

    @Test
    void deliveredAndExpiredAreFinal() {
        assertThat(ShipmentTrackingState.isForward(DELIVERED, COLLECTED)).isFalse();
        assertThat(ShipmentTrackingState.isForward(DELIVERED, EXPIRED)).isFalse();
        assertThat(ShipmentTrackingState.isForward(EXPIRED, DELIVERED)).isFalse();
        assertThat(DELIVERED.isFinal()).isTrue();
        assertThat(EXPIRED.isFinal()).isTrue();
        assertThat(COLLECTED.isFinal()).isFalse();
    }

    @Test
    void parseReadsTheStoredNameAndNullAsNoState() {
        assertThat(ShipmentTrackingState.parse(null)).isNull();
        assertThat(ShipmentTrackingState.parse("DELIVERED")).isEqualTo(DELIVERED);
    }
}

package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentTrackingSubscriptionStateTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 2, 12, 0);

    private static Shipment courier(String trackingNo) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo(trackingNo);
        shipment.setShippedAt(AT);
        return shipment;
    }

    @Test
    void freshShipmentHasNoSubscription() {
        // given
        Shipment shipment = courier("PKG-1");

        // then
        assertThat(shipment.hasTrackingSubscription()).isFalse();
        assertThat(shipment.isTrackingPending()).isFalse();
    }

    @Test
    void markPendingThenActiveKeepsSubscriptionIdAndClearsError() {
        // given
        Shipment shipment = courier("PKG-1");
        shipment.markTrackingFailed("boom", AT);

        // when
        shipment.markTrackingPending("cmd-1", AT);
        boolean pending = shipment.isTrackingPending();
        shipment.markTrackingActive("21037943", AT.plusMinutes(1));

        // then
        assertThat(pending).isTrue();
        assertThat(shipment.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        assertThat(shipment.getTrackingSubscriptionId()).isEqualTo("cmd-1");
        assertThat(shipment.getTrackingExternalId()).isEqualTo("21037943");
        assertThat(shipment.getTrackingSubscriptionError()).isNull();
        assertThat(shipment.getTrackingSubscribedAt()).isEqualTo(AT.plusMinutes(1));
        assertThat(shipment.hasTrackingSubscription()).isTrue();
    }

    @Test
    void markFailedStoresError() {
        // given
        Shipment shipment = courier("PKG-1");

        // when
        shipment.markTrackingFailed("carrier not recognised", AT);

        // then
        assertThat(shipment.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.FAILED);
        assertThat(shipment.getTrackingSubscriptionError()).isEqualTo("carrier not recognised");
        assertThat(shipment.isTrackingPending()).isFalse();
    }

    @Test
    void inheritsSubscriptionAndExternalIdFromPreviousShipmentWithSameTrackingNo() {
        // given
        Shipment previous = courier("PKG-1");
        previous.setExternalId("furg-1");
        previous.markTrackingActive("21037943", AT);
        Shipment edited = courier("PKG-1");

        // when
        edited.inheritTrackingSubscriptionFrom(previous);

        // then
        assertThat(edited.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        assertThat(edited.getTrackingExternalId()).isEqualTo("21037943");
        assertThat(edited.getExternalId()).isEqualTo("furg-1");
    }

    @Test
    void doesNotInheritWhenTrackingNoChanged() {
        // given
        Shipment previous = courier("PKG-1");
        previous.markTrackingActive("21037943", AT);
        Shipment edited = courier("PKG-2");

        // when
        edited.inheritTrackingSubscriptionFrom(previous);

        // then
        assertThat(edited.hasTrackingSubscription()).isFalse();
    }

    @Test
    void hasTrackingNoIgnoresCaseAndSurroundingWhitespace() {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setTrackingNo("acmebtrkd89eaa836bc7");

        assertThat(shipment.hasTrackingNo("ACMEBTRKD89EAA836BC7")).isTrue();
        assertThat(shipment.hasTrackingNo("  acmebtrkd89eaa836bc7 ")).isTrue();
        assertThat(shipment.hasTrackingNo("ACMEBTRKD89EAA836BC8")).isFalse();
        assertThat(shipment.hasTrackingNo(null)).isFalse();
        assertThat(Shipment.normalizeTrackingNo(null)).isNull();
        assertThat(Shipment.normalizeTrackingNo(" pkg-1 ")).isEqualTo("PKG-1");
    }

    @Test
    void whitespaceOnlyTrackingNumberIsNotShippingData() {
        // a blank number would normalize to an empty index key, which DynamoDB rejects
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo("   ");
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 3, 10, 0));

        assertThat(shipment.hasShippingData()).isFalse();
    }
}

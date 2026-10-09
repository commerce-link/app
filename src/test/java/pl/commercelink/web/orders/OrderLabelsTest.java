package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentTrackingStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.warehouse.api.ItemCondition;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class OrderLabelsTest {

    private static final ResourceBundle PL = ResourceBundle.getBundle("messages", Locale.forLanguageTag("pl"));
    private static final ResourceBundle EN = ResourceBundle.getBundle("messages", Locale.ENGLISH);

    private static <E extends Enum<E>> List<String> keys(E[] values, Function<E, String> label) {
        List<String> keys = new ArrayList<>();
        for (E value : values) {
            keys.add(label.apply(value));
        }
        return keys;
    }

    @Test
    void everyEnumShownOnTheOrderScreensHasATextInBothLanguages() {
        // given
        List<String> keys = new ArrayList<>();
        keys.addAll(keys(OrderStatus.values(), OrderLabels::status));
        keys.addAll(keys(FulfilmentStatus.values(), OrderLabels::itemStatus));
        keys.addAll(keys(OrderSourceType.values(), OrderLabels::sourceType));
        keys.addAll(keys(FulfilmentType.values(), OrderLabels::fulfilmentType));
        keys.addAll(keys(FulfilmentType.values(), OrderLabels::fulfilmentTypeShort));
        keys.addAll(keys(ShipmentType.values(), OrderLabels::shipmentType));
        keys.addAll(keys(DocumentType.values(), OrderLabels::documentType));
        keys.addAll(keys(DocumentType.values(), OrderLabels::documentPrefix));
        keys.addAll(keys(PaymentSource.values(), OrderLabels::paymentSource));
        keys.addAll(keys(PaymentDirection.values(), d -> "PaymentDirection." + d.name()));
        keys.addAll(keys(OrderStatus.values(), s -> "order.status.effect." + s.name()));
        keys.addAll(keys(OrderReviewStatus.values(), OrderLabels::reviewStatus));
        keys.addAll(keys(ItemCondition.values(), OrderLabels::condition));
        keys.addAll(keys(ShipmentTrackingStatus.values(), OrderLabels::tracking));

        // then
        assertThat(keys).allSatisfy(key -> {
            assertThat(PL.containsKey(key)).as(key + " in messages_pl").isTrue();
            assertThat(EN.containsKey(key)).as(key + " in messages_en").isTrue();
        });
    }

    @Test
    void orderStatusTonesFollowTheSpec() {
        // when / then
        assertThat(OrderLabels.tone(OrderStatus.New)).isEqualTo("is-info");
        assertThat(OrderLabels.tone(OrderStatus.Blocked)).isEqualTo("is-bad");
        assertThat(OrderLabels.tone(OrderStatus.Assembly)).isEqualTo("is-info");
        assertThat(OrderLabels.tone(OrderStatus.Assembled)).isEqualTo("is-info");
        assertThat(OrderLabels.tone(OrderStatus.Realization)).isEqualTo("is-info");
        assertThat(OrderLabels.tone(OrderStatus.Shipping)).isEqualTo("is-info");
        assertThat(OrderLabels.tone(OrderStatus.Delivered)).isEqualTo("is-ok");
        assertThat(OrderLabels.tone(OrderStatus.Completed)).isEqualTo("is-ok");
        assertThat(OrderLabels.tone(OrderStatus.Cancelled)).isEqualTo("is-neutral");
    }

    @Test
    void statusToneMatchesTheListPill() {
        // when / then
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(OrderLabels.tone(status)).isEqualTo(OrderRowMapper.statusTone(status));
        }
    }

    @Test
    void itemStatusTonesFollowTheSpec() {
        // when / then: each item status has a fixed pill tone
        assertThat(OrderLabels.tone(FulfilmentStatus.New)).isEqualTo("is-neutral");
        assertThat(OrderLabels.tone(FulfilmentStatus.Allocation)).isEqualTo("is-info");
        assertThat(OrderLabels.tone(FulfilmentStatus.Ordered)).isEqualTo("is-info");
        assertThat(OrderLabels.tone(FulfilmentStatus.Reserved)).isEqualTo("is-info");
        assertThat(OrderLabels.tone(FulfilmentStatus.Delivered)).isEqualTo("is-ok");
        assertThat(OrderLabels.tone(FulfilmentStatus.InRMA)).isEqualTo("is-warn");
        assertThat(OrderLabels.tone(FulfilmentStatus.Returned)).isEqualTo("is-warn");
        assertThat(OrderLabels.tone(FulfilmentStatus.Replaced)).isEqualTo("is-warn");
        assertThat(OrderLabels.tone(FulfilmentStatus.InExternalService)).isEqualTo("is-warn");
        assertThat(OrderLabels.tone(FulfilmentStatus.Destroyed)).isEqualTo("is-bad");
    }

    @Test
    void anAbsentValueHasNoLabel() {
        // when / then
        assertThat(OrderLabels.status(null)).isNull();
        assertThat(OrderLabels.tone((OrderStatus) null)).isNull();
        assertThat(OrderLabels.sourceType(null)).isNull();
        assertThat(OrderLabels.fulfilmentTypeShort(null)).isNull();
        assertThat(OrderLabels.fulfilmentTypeIcon(null)).isNull();
    }

    @Test
    void anOrderItemInHandHasItsOwnLabelWhileTheOtherStatesKeepTheEnumKey() {
        // when / then: FulfilmentStatus.Delivered stays the warehouse stock label ("Dostarczony")
        assertThat(OrderLabels.itemStatus(FulfilmentStatus.Delivered)).isEqualTo("order.item.status.Delivered");
        assertThat(OrderLabels.itemStatus(FulfilmentStatus.Ordered)).isEqualTo("FulfilmentStatus.Ordered");
        assertThat(PL.getString("order.item.status.Delivered")).isEqualTo("Skompletowany");
        assertThat(PL.getString("FulfilmentStatus.Delivered")).isEqualTo("Dostarczony");
    }

    @Test
    void fulfilmentTypeIconMatchesEachType() {
        // when / then: a decorative icon paired with the short label, the text carries the meaning
        assertThat(OrderLabels.fulfilmentTypeIcon(FulfilmentType.WarehouseFulfilment)).isEqualTo("fa-warehouse");
        assertThat(OrderLabels.fulfilmentTypeIcon(FulfilmentType.DirectToConsumer)).isEqualTo("fa-truck");
    }

    @Test
    void trackingToneFollowsTheSubscriptionState() {
        // when / then
        assertThat(OrderLabels.tone(ShipmentTrackingStatus.PENDING)).isEqualTo(OrderLabels.NEUTRAL);
        assertThat(OrderLabels.tone(ShipmentTrackingStatus.ACTIVE)).isEqualTo(OrderLabels.INFO);
        assertThat(OrderLabels.tone(ShipmentTrackingStatus.FAILED)).isEqualTo(OrderLabels.BAD);
        assertThat(OrderLabels.tone((ShipmentTrackingStatus) null)).isNull();
    }

    @Test
    void aStoredCauseOfOurOwnIsResolvedAfterTheFailurePrefixWithTheIntegrationsName() {
        // given: the message source the application runs with, which resolves a message given as an argument
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        Shipment notCreated = new Shipment(ShipmentType.Courier);
        notCreated.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()).failedWithKey("shipping.creation.notCreated"));
        Shipment noWindows = new Shipment(ShipmentType.Courier);
        noWindows.setProvider("furgonetka");
        noWindows.setPickup(ShipmentPickup.awaiting().failedWithKey("shipping.pickup.immediate.no.windows"));

        // when
        OrderLabels.ShipmentState creation = OrderLabels.shipmentState(notCreated, Locale.ENGLISH, "Furgonetka");
        OrderLabels.ShipmentState pickup = OrderLabels.shipmentState(noWindows, Locale.ENGLISH, "Furgonetka");

        // then
        assertThat(messages.getMessage(creation.key(), creation.args(), Locale.ENGLISH)).isEqualTo(
                EN.getString("order.shipments.state.creation.failed").replace("{0}",
                        EN.getString("shipping.creation.notCreated").replace("{0}", "Furgonetka")));
        assertThat(messages.getMessage(pickup.key(), pickup.args(), Locale.ENGLISH)).isEqualTo(
                EN.getString("order.shipments.state.pickup.failed").replace("{0}", EN.getString("shipping.pickup.immediate.no.windows")));
    }

    @Test
    void aStoredSentenceThatStatesTheOutcomeIsTheLineItselfWithTheIntegrationsName() {
        // given
        Shipment unconfirmed = new Shipment(ShipmentType.Courier);
        unconfirmed.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()).failedWithKey(ShipmentCreationState.UNCONFIRMED_KEY));
        List<String> pickupKeys = List.of(ShipmentPickup.UNCONFIRMED_KEY, "shipping.pickup.not.sent", "shipping.pickup.no.provider");

        // when / then
        assertThat(OrderLabels.shipmentState(unconfirmed, Locale.ENGLISH, "Furgonetka").key())
                .isEqualTo(ShipmentCreationState.UNCONFIRMED_KEY);
        assertThat(OrderLabels.shipmentState(unconfirmed, Locale.ENGLISH, "Furgonetka").args()).containsExactly("Furgonetka");
        for (String key : pickupKeys) {
            Shipment parcel = new Shipment(ShipmentType.Courier);
            parcel.setProvider("furgonetka");
            parcel.setPickup(ShipmentPickup.awaiting().failedWithKey(key));
            assertThat(OrderLabels.shipmentState(parcel, Locale.ENGLISH, "Furgonetka").key()).isEqualTo(key);
            assertThat(OrderLabels.shipmentState(parcel, Locale.ENGLISH, "Furgonetka").args()).containsExactly("Furgonetka");
        }
    }
}

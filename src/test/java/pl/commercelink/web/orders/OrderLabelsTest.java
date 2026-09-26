package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShipmentTrackingStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.warehouse.api.ItemCondition;

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
        keys.addAll(keys(ShipmentType.values(), OrderLabels::shipmentType));
        keys.addAll(keys(DocumentType.values(), OrderLabels::documentType));
        keys.addAll(keys(PaymentSource.values(), OrderLabels::paymentSource));
        keys.addAll(keys(PaymentDirection.values(), OrderLabels::paymentDirection));
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
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(OrderLabels.tone(status)).isEqualTo(OrderRowMapper.statusTone(status));
        }
    }

    @Test
    void itemStatusTonesFollowTheSpec() {
        // when / then
        assertThat(OrderLabels.tone(FulfilmentStatus.New)).isEqualTo("is-neutral");
        assertThat(OrderLabels.tone(FulfilmentStatus.Allocation)).isEqualTo("is-warn");
        assertThat(OrderLabels.tone(FulfilmentStatus.Ordered)).isEqualTo("is-info");
        assertThat(OrderLabels.tone(FulfilmentStatus.Reserved)).isEqualTo("is-info");
        assertThat(OrderLabels.tone(FulfilmentStatus.Delivered)).isEqualTo("is-ok");
        assertThat(OrderLabels.tone(FulfilmentStatus.InRMA)).isEqualTo("is-bad");
        assertThat(OrderLabels.tone(FulfilmentStatus.InExternalService)).isEqualTo("is-warn");
        assertThat(OrderLabels.tone(FulfilmentStatus.Returned)).isEqualTo("is-neutral");
        assertThat(OrderLabels.tone(FulfilmentStatus.Replaced)).isEqualTo("is-neutral");
        assertThat(OrderLabels.tone(FulfilmentStatus.Destroyed)).isEqualTo("is-neutral");
    }

    @Test
    void anAbsentValueHasNoLabel() {
        // when / then
        assertThat(OrderLabels.status(null)).isNull();
        assertThat(OrderLabels.tone((OrderStatus) null)).isNull();
        assertThat(OrderLabels.sourceType(null)).isNull();
    }
}

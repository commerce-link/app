package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.FulfilmentStatus;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseShippingReservationsTest {

    private static final String STORE_ID = "store-1";

    @Mock private WarehouseRepository warehouseRepository;

    private WarehouseShippingReservations reservations;
    private final Map<String, WarehouseItem> stored = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(warehouseRepository.findById(eq(STORE_ID), anyString())).thenAnswer(i -> stored.get(i.getArgument(1)));
        reservations = new WarehouseShippingReservations(warehouseRepository);
    }

    private WarehouseItem item(String itemId, FulfilmentStatus status) {
        WarehouseItem item = new WarehouseItem(STORE_ID, "delivery-1", "Laptops", "Laptop", "590", "MFN", 100.0, 1);
        item.setItemId(itemId);
        item.setStatus(status);
        stored.put(itemId, item);
        return item;
    }

    @Test
    void availableItemsAreHeldForTheCommand() {
        // given
        item("w-1", FulfilmentStatus.Delivered);
        item("w-2", FulfilmentStatus.Delivered);

        // when
        boolean held = reservations.hold(STORE_ID, List.of("w-1", "w-2"), "cmd-1");

        // then
        assertThat(held).isTrue();
        assertThat(stored.get("w-1").isHeldBy("cmd-1")).isTrue();
        assertThat(stored.get("w-2").isHeldBy("cmd-1")).isTrue();
        assertThat(reservations.canShip(STORE_ID, List.of("w-1"))).isFalse();
    }

    @Test
    void anItemHeldByAnotherShipmentRefusesTheWholeHoldAndLetsTheOthersGo() {
        // given: w-2 is held by a shipment started in another tab
        item("w-1", FulfilmentStatus.Delivered);
        item("w-2", FulfilmentStatus.Delivered).holdForShipping("cmd-other", LocalDateTime.now());

        // when
        boolean held = reservations.hold(STORE_ID, List.of("w-1", "w-2"), "cmd-1");

        // then
        assertThat(held).isFalse();
        assertThat(stored.get("w-1").getShippingCommandId()).isNull();
        assertThat(stored.get("w-2").isHeldBy("cmd-other")).isTrue();
    }

    @Test
    void anItemChangedSinceItWasReadRefusesTheHold() {
        // given: the versioned save loses to a shipment started at the same time
        item("w-1", FulfilmentStatus.Delivered);
        doThrow(new ConditionalCheckFailedException("version"))
                .when(warehouseRepository).save(argThat(i -> "w-1".equals(i.getItemId()) && i.isHeldBy("cmd-1")));

        // when / then
        assertThat(reservations.hold(STORE_ID, List.of("w-1"), "cmd-1")).isFalse();
    }

    @Test
    void anItemNoLongerInStockRefusesTheHold() {
        // given: the first shipment's goods-out already took it out
        item("w-1", FulfilmentStatus.InExternalService);

        // when / then
        assertThat(reservations.hold(STORE_ID, List.of("w-1"), "cmd-1")).isFalse();
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void aHoldNobodySettledExpires() {
        // given
        item("w-1", FulfilmentStatus.Delivered).holdForShipping("cmd-lost", LocalDateTime.now().minusMinutes(11));

        // when / then
        assertThat(reservations.hold(STORE_ID, List.of("w-1"), "cmd-1")).isTrue();
        assertThat(stored.get("w-1").isHeldBy("cmd-1")).isTrue();
    }

    @Test
    void releaseLeavesAHoldOfAnotherCommandAlone() {
        // given
        item("w-1", FulfilmentStatus.Delivered).holdForShipping("cmd-1", LocalDateTime.now());
        item("w-2", FulfilmentStatus.Delivered).holdForShipping("cmd-2", LocalDateTime.now());

        // when
        reservations.release(STORE_ID, List.of("w-1", "w-2"), "cmd-1");

        // then
        assertThat(stored.get("w-1").getShippingCommandId()).isNull();
        assertThat(stored.get("w-2").isHeldBy("cmd-2")).isTrue();
        assertThat(reservations.canShip(STORE_ID, List.of("w-1"))).isTrue();
    }

    @Test
    void anItemInRmaTheWarehouseOffersToShipCanBeHeld() {
        // given: "Wyślij do dystrybutora" is offered for items in RMA, not for items in stock
        item("w-1", FulfilmentStatus.InRMA);

        // when / then
        assertThat(reservations.canShip(STORE_ID, List.of("w-1"))).isTrue();
        assertThat(reservations.hold(STORE_ID, List.of("w-1"), "cmd-1")).isTrue();
    }
}

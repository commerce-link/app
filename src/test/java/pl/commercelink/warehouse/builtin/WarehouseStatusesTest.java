package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.FulfilmentStatus;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseStatusesTest {

    @Test
    void destroyedIsNeutralAndOtherTonesStay() {
        // when / then
        assertThat(WarehouseStatuses.tone(FulfilmentStatus.Destroyed)).isEqualTo("is-neutral");
        assertThat(WarehouseStatuses.tone(FulfilmentStatus.Delivered)).isEqualTo("is-ok");
        assertThat(WarehouseStatuses.tone(FulfilmentStatus.InRMA)).isEqualTo("is-warn");
        assertThat(WarehouseStatuses.tone(FulfilmentStatus.New)).isEqualTo("is-neutral");
        assertThat(WarehouseStatuses.tone(FulfilmentStatus.Reserved)).isEqualTo("is-info");
    }
}

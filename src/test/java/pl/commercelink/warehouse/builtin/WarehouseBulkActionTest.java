package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.FulfilmentStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.orders.FulfilmentStatus.*;

class WarehouseBulkActionTest {

    private static WarehouseItem item(FulfilmentStatus status) {
        WarehouseItem item = new WarehouseItem("store-1", "d1", "GPU", "RTX", "590", "MFN", 100, 2);
        item.setStatus(status);
        return item;
    }

    @Test
    void matrixMatchesTheOldPerStatusMenus() {
        // given
        // when / then
        assertThat(WarehouseBulkAction.RESERVE.allowed()).containsExactly(Delivered);
        assertThat(WarehouseBulkAction.RELEASE.allowed()).containsExactly(Reserved, InRMA);
        assertThat(WarehouseBulkAction.RMA.allowed()).containsExactly(Delivered, Reserved);
        assertThat(WarehouseBulkAction.ALLOCATE.allowed()).containsExactly(New);
        assertThat(WarehouseBulkAction.EXTERNAL_SERVICE.allowed()).containsExactly(InRMA);
        assertThat(WarehouseBulkAction.RECEIVE.allowed()).containsExactly(InExternalService);
        assertThat(WarehouseBulkAction.SHIP.allowed()).containsExactly(InRMA);
        assertThat(WarehouseBulkAction.DESTROY.allowed()).containsExactly(Delivered, InRMA, InExternalService);
    }

    @Test
    void pathsKeepTheExistingEndpoints() {
        // given
        // when / then
        assertThat(WarehouseBulkAction.RESERVE.path()).isEqualTo("/dashboard/warehouse/markAsReserved");
        assertThat(WarehouseBulkAction.SHIP.path()).isEqualTo("/dashboard/warehouse/shipping");
        assertThat(WarehouseBulkAction.DESTROY.path()).isEqualTo("/dashboard/warehouse/markAsDestroyed");
    }

    @Test
    void dialogKindsFollowTheSpec() {
        // given
        // when / then
        assertThat(WarehouseBulkAction.RESERVE.needsQuantity()).isTrue();
        assertThat(WarehouseBulkAction.DESTROY.needsQuantity()).isTrue();
        assertThat(WarehouseBulkAction.DESTROY.danger()).isTrue();
        assertThat(WarehouseBulkAction.EXTERNAL_SERVICE.needsConfirm()).isTrue();
        assertThat(WarehouseBulkAction.EXTERNAL_SERVICE.sameSource()).isTrue();
        assertThat(WarehouseBulkAction.SHIP.needsConfirm()).isFalse();
        assertThat(WarehouseBulkAction.SHIP.sameSource()).isTrue();
        assertThat(WarehouseBulkAction.menu()).doesNotContain(WarehouseBulkAction.DESTROY).hasSize(7);
    }

    @Test
    void firstRefusedNamesTheItemInTheWrongStatus() {
        // given
        WarehouseItem ok = item(Delivered);
        WarehouseItem wrong = item(Reserved);
        // when / then
        assertThat(WarehouseBulkAction.RESERVE.firstRefused(List.of(ok))).isEmpty();
        assertThat(WarehouseBulkAction.RESERVE.firstRefused(List.of(ok, wrong))).contains(wrong);
    }
}

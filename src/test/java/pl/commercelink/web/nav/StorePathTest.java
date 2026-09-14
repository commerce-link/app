package pl.commercelink.web.nav;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorePathTest {

    @Test
    void readsTheStoreIdFromAStoreScopedPath() {
        // when / then
        assertThat(StorePath.storeIdIn("/dashboard/store/uma2dqukxr/deliveries")).isEqualTo("uma2dqukxr");
        assertThat(StorePath.storeIdIn("/dashboard/store/uma2dqukxr")).isEqualTo("uma2dqukxr");
    }

    @Test
    void treatsReservedSegmentsAsPagesNotStoreIds() {
        // when / then
        assertThat(StorePath.storeIdIn("/dashboard/store/rma-centers")).isNull();
        assertThat(StorePath.storeIdIn("/dashboard/store/invoicing")).isNull();
        assertThat(StorePath.storeIdIn("/dashboard/store/company-details")).isNull();
    }

    @Test
    void findsNoStoreIdOutsideTheStorePrefix() {
        // when / then
        assertThat(StorePath.storeIdIn("/dashboard/orders")).isNull();
        assertThat(StorePath.storeIdIn("/dashboard/stores")).isNull();
    }

    @Test
    void stripsTheStorePrefixSoNavigationMatchesTheGlobalPaths() {
        // when / then
        assertThat(StorePath.stripStorePrefix("/dashboard/store/uma2dqukxr/warehouse-documents/details"))
                .isEqualTo("/dashboard/warehouse-documents/details");
        assertThat(StorePath.stripStorePrefix("/dashboard/store/uma2dqukxr")).isEqualTo("/dashboard/store");
    }

    @Test
    void leavesPathsWithoutAStoreIdUntouched() {
        // when / then
        assertThat(StorePath.stripStorePrefix("/dashboard/store/rma-centers")).isEqualTo("/dashboard/store/rma-centers");
        assertThat(StorePath.stripStorePrefix("/dashboard/orders")).isEqualTo("/dashboard/orders");
    }
}

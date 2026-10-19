package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoreSupplierConnectionTest {

    @Test
    void defaultsBothIncludeFlagsToTrue() {
        // given / when
        StoreSupplierConnection connection = new StoreSupplierConnection("AbGroup", ConnectionMode.GLOBAL);

        // then
        assertThat(connection.isIncludeInPricing()).isTrue();
        assertThat(connection.isIncludeInFulfilment()).isTrue();
    }
}

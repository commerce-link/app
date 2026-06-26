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

    @Test
    void carriesExplicitIncludeFlags() {
        // given / when
        StoreSupplierConnection connection =
                new StoreSupplierConnection("AbGroup", ConnectionMode.OWN, false, true);

        // then
        assertThat(connection.getSupplierName()).isEqualTo("AbGroup");
        assertThat(connection.getMode()).isEqualTo(ConnectionMode.OWN);
        assertThat(connection.isIncludeInPricing()).isFalse();
        assertThat(connection.isIncludeInFulfilment()).isTrue();
    }
}

package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.OrderSourceType;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptConfigurationTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 9, 23, 10, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 9, 24, 10, 0);

    @Test
    void defaultsCoverEverySourceButPointOfSale() {
        ReceiptConfiguration configuration = new ReceiptConfiguration();

        assertThat(configuration.isEnabled()).isFalse();
        assertThat(configuration.covers(OrderSourceType.WebStore)).isTrue();
        assertThat(configuration.covers(OrderSourceType.Marketplace)).isTrue();
        assertThat(configuration.covers(OrderSourceType.PointOfSale)).isFalse();
        assertThat(configuration.covers(null)).isFalse();
    }

    @Test
    void enablingStampsTheMomentOnlyOnTheTransition() {
        ReceiptConfiguration configuration = new ReceiptConfiguration();

        configuration.enable(T1);
        configuration.enable(T2);

        assertThat(configuration.getEnabledAt()).isEqualTo(T1);
    }

    @Test
    void reEnablingMovesTheMoment() {
        ReceiptConfiguration configuration = new ReceiptConfiguration();
        configuration.enable(T1);

        configuration.disable();
        configuration.enable(T2);

        assertThat(configuration.getEnabledAt()).isEqualTo(T2);
    }

    @Test
    void chosenSourcesReplaceTheDefaults() {
        ReceiptConfiguration configuration = new ReceiptConfiguration();

        configuration.setSourceTypes(Set.of(OrderSourceType.PointOfSale));

        assertThat(configuration.covers(OrderSourceType.PointOfSale)).isTrue();
        assertThat(configuration.covers(OrderSourceType.WebStore)).isFalse();
    }

    @Test
    void storeNeverReturnsNullConfiguration() {
        assertThat(new Store().getReceiptConfiguration()).isNotNull();
    }

    @Test
    void defaultSourceTypesCannotBeModified() {
        ReceiptConfiguration configuration = new ReceiptConfiguration();

        assertThatThrownBy(() -> configuration.sourceTypes().add(OrderSourceType.PointOfSale))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ReceiptConfiguration.DEFAULT_SOURCES.add(OrderSourceType.PointOfSale))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(ReceiptConfiguration.DEFAULT_SOURCES).doesNotContain(OrderSourceType.PointOfSale);
    }
}

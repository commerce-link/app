package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClientOrderItemStatusTest {

    @ParameterizedTest
    @CsvSource({
            "New, Allocation",
            "Allocation, Allocation",
            "Ordered, AwaitingDelivery",
            "Reserved, Assembled",
            "Delivered, Assembled",
            "InRMA, InClaim",
            "InExternalService, InClaim",
            "Returned, Returned",
            "Replaced, Returned",
            "Destroyed, Returned"
    })
    @DisplayName("from hides the New/Allocation distinction and maps the remaining statuses onto customer labels")
    void fromMapsFulfilmentStatusesOntoCustomerLabels(FulfilmentStatus status, ClientOrderItemStatus expected) {
        // when / then
        assertThat(ClientOrderItemStatus.from(status)).isEqualTo(expected);
    }
}

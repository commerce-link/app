package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClientOrderStageTest {

    @ParameterizedTest
    @CsvSource({
            "New, Accepted",
            "Blocked, Accepted",
            "Assembly, Assembly",
            "Assembled, Preparation",
            "Realization, Preparation",
            "Shipping, Shipping",
            "Delivered, Delivered",
            "Completed, Delivered"
    })
    @DisplayName("from maps every trackable order status onto a customer-facing stage")
    void fromMapsTrackableStatusesOntoStages(OrderStatus status, ClientOrderStage expected) {
        // when / then
        assertThat(ClientOrderStage.from(status)).contains(expected);
    }

    @Test
    @DisplayName("from yields no stage for a cancelled order")
    void fromYieldsNoStageForCancelledOrder() {
        // when / then
        assertThat(ClientOrderStage.from(OrderStatus.Cancelled)).isEmpty();
    }

    @Test
    @DisplayName("isReachedBy is true only for stages strictly before the current one")
    void isReachedByIsTrueOnlyForEarlierStages() {
        // given
        ClientOrderStage current = ClientOrderStage.Preparation;

        // when / then
        assertThat(ClientOrderStage.Accepted.isReachedBy(current)).isTrue();
        assertThat(ClientOrderStage.Assembly.isReachedBy(current)).isTrue();
        assertThat(ClientOrderStage.Preparation.isReachedBy(current)).isFalse();
        assertThat(ClientOrderStage.Shipping.isReachedBy(current)).isFalse();
        assertThat(ClientOrderStage.Accepted.isReachedBy(null)).isFalse();
    }

    @Test
    @DisplayName("isReachedBy treats the final stage as completed once the order arrives at it")
    void isReachedByTreatsFinalStageAsCompleted() {
        // when / then
        assertThat(ClientOrderStage.Delivered.isReachedBy(ClientOrderStage.Delivered)).isTrue();
        assertThat(ClientOrderStage.Shipping.isReachedBy(ClientOrderStage.Shipping)).isFalse();
    }
}

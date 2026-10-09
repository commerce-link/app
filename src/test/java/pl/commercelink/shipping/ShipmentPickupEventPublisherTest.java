package pl.commercelink.shipping;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.commercelink.shipping.api.PickupWindow;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentPickupEventPublisherTest {

    @Test
    void checksWaitLongerWithEveryAttemptAndStayAtTheLongestDelay() {
        // when / then
        assertThat(ShipmentPickupEventPublisher.delayFor(1)).isEqualTo(3);
        assertThat(ShipmentPickupEventPublisher.delayFor(2)).isEqualTo(5);
        assertThat(ShipmentPickupEventPublisher.delayFor(ShipmentPickupChecker.MAX_ATTEMPTS)).isEqualTo(60);
        assertThat(ShipmentPickupEventPublisher.delayFor(20)).isEqualTo(60);
        assertThat(ShipmentPickupEventPublisher.delayFor(0)).isEqualTo(3);
    }

    @Test
    void theMessageCarriesTheTargetsAndTheWindowThroughJson() throws Exception {
        // given
        PickupWindow window = new PickupWindow(LocalDate.of(2026, 10, 7), LocalTime.of(9, 0), LocalTime.of(17, 0), "h");
        ShipmentPickupCheckRequest request = ShipmentPickupCheckRequest.of("store-1", "furgonetka", "cmd-1",
                List.of(new PickupTarget(ShipmentOwnerType.RMA, "rma-1", "1", "TRK-1")), window);
        ObjectMapper json = new ObjectMapper();

        // when
        ShipmentPickupCheckRequest read = json.readValue(json.writeValueAsString(request), ShipmentPickupCheckRequest.class);

        // then
        assertThat(read).isEqualTo(request);
        assertThat(read.window()).isEqualTo(window);
    }
}

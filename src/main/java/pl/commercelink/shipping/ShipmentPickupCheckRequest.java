package pl.commercelink.shipping;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.commercelink.shipping.api.PickupWindow;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Everything the pickup checker needs to settle a command on every owner of its packages. */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ShipmentPickupCheckRequest {

    private String storeId;
    private String provider;
    private String commandId;
    private List<PickupTarget> targets;
    // the chosen window as text, so the message stays plain JSON
    private String date;
    private String from;
    private String to;
    private String token;
    private int attempt;

    public PickupWindow window() {
        return new PickupWindow(LocalDate.parse(date), LocalTime.parse(from), LocalTime.parse(to), token);
    }

    public ShipmentPickupCheckRequest nextAttempt() {
        return toBuilder().attempt(attempt + 1).build();
    }

    static ShipmentPickupCheckRequest of(String storeId, String provider, String commandId, List<PickupTarget> targets,
                                         PickupWindow window) {
        return builder().storeId(storeId).provider(provider).commandId(commandId).targets(List.copyOf(targets))
                .date(window.date().toString()).from(window.from().toString()).to(window.to().toString())
                .token(window.token()).attempt(1).build();
    }
}

package pl.commercelink.shipping;

import java.util.List;

/**
 * The "Wyślij przez" card of the shipping page: the options, the one shown (selected), the sentence under them
 * (why one is suggested, or null) and the warning of shipping an Allegro order through another integration (or null).
 * Not shown with a single available option: there is nothing to choose and nothing to explain.
 */
public record ShippingIntegrationChoiceView(List<ShippingIntegrationOption> options, String selected, String help,
                                            String warning) {

    public boolean shown() {
        return options.size() > 1 || options.stream().anyMatch(o -> !o.available());
    }

    /** No integration can ship the order: the page offers neither Allegro's form nor the default integration's steps. */
    public boolean nothingAvailable() {
        return selected == null;
    }

    public boolean isSelected(ShippingIntegrationOption option) {
        return option.name().equals(selected);
    }
}

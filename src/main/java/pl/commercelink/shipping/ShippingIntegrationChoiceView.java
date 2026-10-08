package pl.commercelink.shipping;

import java.util.List;

/**
 * The "Wyślij przez" card of the shipping page: the options, the one shown (selected), the sentence under them
 * (why one is suggested, or null) and the warning of shipping an Allegro order through another integration (or null).
 * Not shown with a single option: there is nothing to choose.
 */
public record ShippingIntegrationChoiceView(List<ShippingIntegrationOption> options, String selected, String help,
                                            String warning) {

    public boolean shown() {
        return options.size() > 1;
    }

    public boolean isSelected(ShippingIntegrationOption option) {
        return option.name().equals(selected);
    }
}

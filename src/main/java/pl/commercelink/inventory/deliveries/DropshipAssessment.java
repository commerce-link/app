package pl.commercelink.inventory.deliveries;

import java.util.List;

/**
 * The outcome of checking an order for dropshipping: every supplier that can ship straight to the customer,
 * or the reason why none can. An order may legitimately have several suppliers - each one becomes its own
 * dropship delivery.
 */
public record DropshipAssessment(List<String> providers, DropshipRejection rejection) {

    public DropshipAssessment {
        providers = List.copyOf(providers);
    }

    public static DropshipAssessment of(List<String> providers) {
        return new DropshipAssessment(providers, null);
    }

    public static DropshipAssessment rejected(DropshipRejection rejection) {
        return new DropshipAssessment(List.of(), rejection);
    }

    public boolean hasProviders() {
        return !providers.isEmpty();
    }

    public boolean supports(String provider) {
        return provider != null && providers.contains(provider);
    }
}

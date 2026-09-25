package pl.commercelink.products;

import java.util.List;

/**
 * The pricing of the production category "Monitory" (catalog "Akcesoria", Automatic), read on 2026-09-23: eleven price
 * definitions in the stored order. "Ultra Premium" and "Premium" repeat, one row per (label, price threshold) rule, with
 * the same price parameters in every row of a group; "Default" is not repeated.
 */
public final class MonitoryPricingFixture {

    private MonitoryPricingFixture() {
    }

    public static CategoryDefinition monitory() {
        CategoryDefinition category = new CategoryDefinition().withName("Monitory").withGeneratedId()
                .withStockDefinition(new StockDefinition(1, 10, 30))
                .withAvailabilityDefinition(new AvailabilityDefinition(3, 1));
        category.setPriceDefinitions(definitions());
        return category;
    }

    /** The eleven definitions in the order DynamoDB holds them. */
    public static List<PriceDefinition> definitions() {
        return List.of(
                ultraPremium("1440p, Ultrawide, 240+ Hz", 4000),
                ultraPremium("1440p, Ultrawide, 144+ Hz", 4000),
                ultraPremium("4K, 32\", 144+ Hz", 4000),
                ultraPremium("4K, 32\", 240+ Hz", 4000),
                ultraPremium("1440p, 27\", 240+ Hz", 3500),
                ultraPremium("4K, 27\", 144+ Hz", 3500),
                premium("1440p, Ultrawide, 144+ Hz", 3000),
                premium("4K, 32\", 144+ Hz", 3000),
                premium("1440p, 27\", 240+ Hz", 2500),
                premium("4K, 27\", 144+ Hz", 2500),
                new PriceDefinition(1.06, 49, 39, 29, 19, PriceDefinition.DEFAULT_PRICING_GROUP));
    }

    private static PriceDefinition ultraPremium(String label, double priceMatch) {
        return rule(new PriceDefinition(1.1, 399, 199, 99, 49, "Ultra Premium"), label, priceMatch);
    }

    private static PriceDefinition premium(String label, double priceMatch) {
        return rule(new PriceDefinition(1.08, 299, 99, 49, 39, "Premium"), label, priceMatch);
    }

    private static PriceDefinition rule(PriceDefinition definition, String label, double priceMatch) {
        definition.setLabelMatch(label);
        definition.setPriceMatch(priceMatch);
        return definition;
    }
}

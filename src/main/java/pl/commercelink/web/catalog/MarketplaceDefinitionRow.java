package pl.commercelink.web.catalog;

import pl.commercelink.products.MarketplaceDefinition;

import java.util.Optional;

/** One marketplace of the store on the category's marketplaces page, with the state of its export definition. */
public record MarketplaceDefinitionRow(String name, String displayName, State state, Optional<MarketplaceDefinition> definition,
                                       int approvedProducts, String editHref, String deleteHref, boolean connected) {

    /** Stands in the address for a definition saved without a marketplace name, which has nothing else to be addressed by. */
    public static final String UNNAMED = "_unnamed_";

    public enum State { EXPORTING, DISABLED, INCOMPLETE, NOT_CONFIGURED, ORPHANED }

    public static MarketplaceDefinitionRow of(String catalogId, String categoryId, String name, String displayName,
                                              Optional<MarketplaceDefinition> definition, int approvedProducts, boolean connected) {
        State state;
        if (!connected) {
            state = State.ORPHANED;
        } else if (definition.isEmpty()) {
            state = State.NOT_CONFIGURED;
        } else if (!definition.get().isComplete()) {
            state = State.INCOMPLETE;
        } else if (!definition.get().isEnabled()) {
            state = State.DISABLED;
        } else {
            state = State.EXPORTING;
        }
        String pathName = name == null ? UNNAMED : name;
        return new MarketplaceDefinitionRow(name, displayName, state, definition, approvedProducts,
                CatalogPaths.categoryMarketplace(catalogId, categoryId, pathName),
                CatalogPaths.categoryMarketplaceDelete(catalogId, categoryId, pathName), connected);
    }

    public String stateKey() {
        return "catalog.category.marketplace.state." + state.name();
    }

    public String stateTone() {
        return switch (state) {
            case EXPORTING -> "is-ok";
            case INCOMPLETE, ORPHANED -> "is-warn";
            default -> "is-neutral";
        };
    }

    public boolean hasDefinition() {
        return definition.isPresent();
    }
}

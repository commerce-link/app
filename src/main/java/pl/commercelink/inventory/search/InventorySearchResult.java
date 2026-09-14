package pl.commercelink.inventory.search;

import java.util.List;

public sealed interface InventorySearchResult {

    enum Kind {
        FOUND,
        KNOWN_WITHOUT_OFFERS,
        NOT_FOUND
    }

    Kind kind();

    record Found(MatchedBy matchedBy, ProductHeader product, List<OfferRow> supplierOffers,
                 List<WarehouseRow> warehouseRows, PriceSummary prices, boolean warehouseChecked) implements InventorySearchResult {
        @Override
        public Kind kind() {
            return Kind.FOUND;
        }
    }

    record KnownWithoutOffers(MatchedBy matchedBy, ProductHeader product) implements InventorySearchResult {
        @Override
        public Kind kind() {
            return Kind.KNOWN_WITHOUT_OFFERS;
        }
    }

    record NotFound(String query) implements InventorySearchResult {
        @Override
        public Kind kind() {
            return Kind.NOT_FOUND;
        }
    }
}

package pl.commercelink.inventory;

import java.util.Collection;
import java.util.stream.Stream;

class ListingInventory {

    private final Collection<MatchedInventory> global;
    private final Collection<MatchedInventory> own;

    ListingInventory(Collection<MatchedInventory> global, Collection<MatchedInventory> own) {
        this.global = global;
        this.own = own;
    }

    Stream<MatchedInventory> stream() {
        if (own.isEmpty()) {
            return global.stream();
        }
        ProductIdentifiers globalProducts = ProductIdentifiers.of(global);
        return Stream.concat(global.stream(),
                own.stream().filter(group -> !globalProducts.contains(group.getInventoryKey())));
    }
}

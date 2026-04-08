package pl.commercelink.products.filters;

import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.starter.dynamodb.MetadataBasedObject;
import pl.commercelink.starter.dynamodb.MetadataField;
import pl.commercelink.inventory.MatchedInventory;

import java.util.List;

public abstract class InventoryFilter extends MetadataBasedObject<InventoryFilterType> {

    public InventoryFilter(InventoryFilterType type, List<MetadataField> fieldsList) {
        super(type, fieldsList);
    }

    public abstract boolean run(MatchedInventory matchedInventory, List<Metadata> metadata);
}

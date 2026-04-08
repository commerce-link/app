package pl.commercelink.products.filters;

import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.starter.dynamodb.MetadataField;
import pl.commercelink.inventory.MatchedInventory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static pl.commercelink.starter.util.ConversionUtil.asDistinctCollectionFromCommaSeparateText;

class EanNotEqInventoryFilter extends InventoryFilter {

    private static final List<MetadataField> FIELDS_LIST = new LinkedList<>();

    private static final String EAN_FIELD_NAME = "Eans";

    static {
        FIELDS_LIST.add(new MetadataField(EAN_FIELD_NAME, "Comma separated list of EANs"));
    }

    public EanNotEqInventoryFilter() {
        super(InventoryFilterType.EAN_NOT_EQ, FIELDS_LIST);
    }

    @Override
    public boolean run(MatchedInventory matchedInventory, List<Metadata> metadata) {
        Collection<String> eans = asDistinctCollectionFromCommaSeparateText(getMetadataByKey(metadata, EAN_FIELD_NAME).getValue());
        Collection<String> matchedEans = matchedInventory.getInventoryKey().getProductEans();
        return matchedEans.stream().noneMatch(eans::contains);
    }
}

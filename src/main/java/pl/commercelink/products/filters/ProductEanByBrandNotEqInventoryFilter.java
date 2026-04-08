package pl.commercelink.products.filters;

import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.starter.dynamodb.MetadataField;
import pl.commercelink.inventory.MatchedInventory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static pl.commercelink.starter.util.ConversionUtil.asDistinctCollectionFromCommaSeparateText;

public class ProductEanByBrandNotEqInventoryFilter extends InventoryFilter {

    private static final List<MetadataField> FIELDS_LIST = new LinkedList<>();

    static {
        FIELDS_LIST.add(new MetadataField("$Brand", "Comma separated list of product eans for each brand."));
    }

    public ProductEanByBrandNotEqInventoryFilter() {
        super(InventoryFilterType.PRODUCT_LINE_BY_BRAND_NOT_CONTAIN, FIELDS_LIST);
    }

    @Override
    public boolean run(MatchedInventory matchedInventory, List<Metadata> metadata) {
        String brand = matchedInventory.getTaxonomy().brand();

        if (metadata.stream().noneMatch(m -> m.hasKey(brand))) {
            return true;
        }

        Metadata meta = getMetadataByKey(metadata, brand);
        Collection<String> eans = asDistinctCollectionFromCommaSeparateText(meta.getValue());

        return matchedInventory.getEans().stream().noneMatch(eans::contains);
    }

    @Override
    public boolean canRun(List<Metadata> metadata) {
        return metadata != null && metadata.stream().anyMatch(Metadata::isComplete);
    }
}

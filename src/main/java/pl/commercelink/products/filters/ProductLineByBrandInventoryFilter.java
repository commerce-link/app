package pl.commercelink.products.filters;

import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.starter.dynamodb.MetadataField;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.Taxonomy;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static pl.commercelink.starter.util.ConversionUtil.asDistinctCollectionFromCommaSeparateText;

public class ProductLineByBrandInventoryFilter extends InventoryFilter {

    private static final List<MetadataField> FIELDS_LIST = new LinkedList<>();

    static {
        FIELDS_LIST.add(new MetadataField("$Brand", "Comma separated list of product lines for each brand."));
    }

    public ProductLineByBrandInventoryFilter() {
        super(InventoryFilterType.PRODUCT_LINE_BY_BRAND, FIELDS_LIST);
    }

    @Override
    public boolean run(MatchedInventory matchedInventory, List<Metadata> metadata) {
        Taxonomy taxonomy = matchedInventory.getTaxonomy();

        if (metadata.stream().noneMatch(m -> m.hasKey(taxonomy.brand()))) {
            return false;
        }

        Metadata meta = getMetadataByKey(metadata, taxonomy.brand());
        Collection<String> keywords = asDistinctCollectionFromCommaSeparateText(meta.getValue());
        return containsKeyword(taxonomy.name(), keywords);
    }

    private boolean containsKeyword(String name, Collection<String> keywords) {
        for (String keyword : keywords) {
            if (name.toLowerCase().contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canRun(List<Metadata> metadata) {
        return metadata != null && metadata.stream().anyMatch(Metadata::isComplete);
    }
}

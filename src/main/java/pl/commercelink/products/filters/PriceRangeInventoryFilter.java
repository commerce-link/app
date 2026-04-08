package pl.commercelink.products.filters;

import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.starter.dynamodb.MetadataField;
import pl.commercelink.inventory.MatchedInventory;

import java.util.LinkedList;
import java.util.List;

class PriceRangeInventoryFilter extends InventoryFilter {

    private static final List<MetadataField> FIELDS_LIST = new LinkedList<>();

    private static final String MIN_PRICE_FIELD_NAME = "MinPrice";
    private static final String MAX_PRICE_FIELD_NAME = "MaxPrice";

    static {
        FIELDS_LIST.add(new MetadataField(MIN_PRICE_FIELD_NAME, "Min gross price of the product as int"));
        FIELDS_LIST.add(new MetadataField(MAX_PRICE_FIELD_NAME, "Max gross price of the product as int"));
    }

    public PriceRangeInventoryFilter() {
        super(InventoryFilterType.PRICE_RANGE, FIELDS_LIST);
    }

    @Override
    public boolean run(MatchedInventory matchedInventory, List<Metadata> metadata) {
        int minPrice = getFieldValueByKey(metadata, MIN_PRICE_FIELD_NAME, 0);
        int maxPrice = getFieldValueByKey(metadata, MAX_PRICE_FIELD_NAME, Integer.MAX_VALUE);

        double lowestGrossPriceForInventoryItem = matchedInventory.getLowestPrice().grossValue();

        return lowestGrossPriceForInventoryItem >= minPrice && lowestGrossPriceForInventoryItem <= maxPrice;
    }

    private int getFieldValueByKey(List<Metadata> metadata, String key, int _default) {
        return metadata.stream()
                .filter(m -> m.getKey().equals(key))
                .findFirst()
                .map(m -> Integer.parseInt(m.getValue()))
                .orElse(_default);
    }
}

package pl.commercelink.products.information;

import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.taxonomy.BrandMapper;
import pl.commercelink.products.CategoryDefinitionType;

class PimQueuePriorityCalculator {

    private static final int MAX_VALUE = 100;
    private static final int PROVIDER_COUNT_WEIGHT = 5;
    private static final int AVAILABILITY_WEIGHT = 1;

    static int calculatePriority(String brand,
                                 CategoryDefinitionType catalogType,
                                 MatchedInventory inventory) {
        int priority = 0;
        if (catalogType == CategoryDefinitionType.Managed) {
            priority = MAX_VALUE;
        }

        if (inventory != null && !inventory.isEmpty()) {
            int providerCount = inventory.getSuppliers().size();
            priority += providerCount * PROVIDER_COUNT_WEIGHT;

            long totalAvailability = inventory.getTotalAvailableQty();
            priority += (int) (totalAvailability * AVAILABILITY_WEIGHT);
        }

        priority *= BrandMapper.getStrength(brand);
        return Math.min(priority, MAX_VALUE);
    }

}

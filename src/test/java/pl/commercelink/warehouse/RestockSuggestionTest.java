package pl.commercelink.warehouse;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.InventoryItem;

import static org.assertj.core.api.Assertions.assertThat;

class RestockSuggestionTest {

    @Test
    void givesTheNetLimitAsAGrossPriceForTheFulfilmentScreen() {
        // given
        StockProductLevel level = new StockProductLevel("GPU", "MFN-1", "GPU", 0, 0, 1);
        level.setRestockPriceLowest(81.63);
        InventoryItem offer = new InventoryItem("EAN", "MFN-1", 81.63, "PLN", 5, 1, "Action");
        RestockSuggestion suggestion = new RestockSuggestion(level, offer, RestockPriceCategory.LowestPrice);

        // when
        double gross = suggestion.restockPriceGrossFor(RestockPriceCategory.LowestPrice);

        // then
        assertThat(gross).isEqualTo(100.40);
    }
}

package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.web.dtos.GlobalInventoryAvailabilityAndPriceDto;

@RestController
@RequestMapping("/Global/Inventory")
public class GlobalInventoryRestApi {

    @Autowired
    private Inventory inventory;

    @GetMapping("/AvailabilityAndPrice")
    @ResponseBody
    private GlobalInventoryAvailabilityAndPriceDto availabilityAndPrice(
            @RequestParam(value = "ean", defaultValue = "") String ean) {
        MatchedInventory matchedInventory = inventory.withGlobalData().findByEan(ean);

        return new GlobalInventoryAvailabilityAndPriceDto(
                matchedInventory.getLowestPrice().grossValue(),
                matchedInventory.getTotalAvailableQty()
        );
    }
}

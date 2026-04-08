package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.web.dtos.GlobalInventoryAvailabilityAndPriceDto;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@RestController
@RequestMapping("/Global/Inventory")
public class GlobalInventoryRestApi {

    @Autowired
    private Inventory inventory;

    @GetMapping("/AvailabilityAndPrice")
    @ResponseBody
    private GlobalInventoryAvailabilityAndPriceDto availabilityAndPrice(
            @RequestParam(value = "manufacturerCode", defaultValue = "") String manufacturerCode,
            @RequestParam(value = "ean", defaultValue = "") String ean) {
        InventoryView inventoryView = inventory.withGlobalData();

        MatchedInventory matchedInventory;
        if (isNotBlank(manufacturerCode)) {
            matchedInventory = inventoryView.findByProductCode(manufacturerCode);
        } else {
            matchedInventory = inventoryView.findByEan(ean);
        }

        return new GlobalInventoryAvailabilityAndPriceDto(
                matchedInventory.getLowestPrice().grossValue(),
                matchedInventory.getTotalAvailableQty()
        );
    }
}

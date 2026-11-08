package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.web.dtos.ItemAddInventoryRow;

import java.util.List;

@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class ItemAddInventoryController extends BaseController {

    private final Inventory inventory;

    @GetMapping("/dashboard/item-add/inventory")
    @ResponseBody
    public List<ItemAddInventoryRow> find(@RequestParam String ean) {
        if (StringUtils.isBlank(ean)) {
            return List.of();
        }
        MatchedInventory matched = inventory.withEnabledSuppliersOnly(getStoreId()).findByEan(ean.trim());
        return List.of(matched.hasAnyOffers() ? ItemAddInventoryRow.of(matched) : ItemAddInventoryRow.missing(ean.trim(), matched));
    }
}

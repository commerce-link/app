package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.PricelistFinder;

import java.util.List;

@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class PricelistController extends BaseController {

    private final PricelistFinder pricelistFinder;

    @GetMapping("/dashboard/pricelist")
    @ResponseBody
    public List<AvailabilityAndPrice> getPricelistForCatalog(@RequestParam String catalogId) {
        return pricelistFinder.availabilityAndPrices(getStoreId(), catalogId);
    }
}

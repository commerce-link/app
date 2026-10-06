package pl.commercelink.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.web.orders.OrderLinks;

import java.util.regex.Pattern;

/**
 * The address a printed order card's QR code carries (OrderLinks.scan): it only redirects to the order page of whoever
 * scans it. It reads nothing; the order page itself answers 404 for an order that is gone.
 */
@Controller
public class OrderScanController extends BaseController {

    /** Store and order ids are letters, digits and dashes; anything else never reaches a redirect. */
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @GetMapping("/dashboard/scan/orders/{storeId}/{orderId}")
    public String openScannedOrder(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId) {
        if (!ID.matcher(storeId).matches() || !ID.matcher(orderId).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (isSuperAdmin()) {
            return "redirect:" + OrderLinks.detailsOf(storeId, orderId, true);
        }
        if (!storeId.equals(getStoreId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "redirect:" + OrderLinks.detailsOf(storeId, orderId, false);
    }
}

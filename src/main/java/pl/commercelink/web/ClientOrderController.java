package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.CategoryLocalizer;
import pl.commercelink.web.dtos.ClientOrderView;

@Controller
@RequiredArgsConstructor
@RequestMapping("/store/{storeId}/client/order/{orderId}")
public class ClientOrderController {

    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final StoresRepository storesRepository;
    private final CategoryLocalizer categoryLocalizer;

    @GetMapping("")
    public String getOrderForClient(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId, Model model) {
        Order order = ordersRepository.findById(storeId, orderId);
        // A completed order has nothing left to track; the link expires with it rather than staying public forever.
        if (order == null || order.hasStatus(OrderStatus.Completed)) {
            return "error/404";
        }

        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return "error/404";
        }

        ClientOrderView view = ClientOrderView.from(order, orderItemsRepository.findByOrderId(orderId), store, categoryLocalizer);

        model.addAttribute("view", view);
        model.addAttribute("store", store);
        model.addAttribute("branding", store.getBranding() != null ? store.getBranding() : new Branding());

        return "clientOrder";
    }
}

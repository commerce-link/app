package pl.commercelink.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.starter.security.CustomSecurityContext;

abstract class BaseController {

    /** The one shape of "load the order or answer 404" shared by every orders controller. */
    Order requireOrder(OrdersRepository ordersRepository, String storeId, String orderId) {
        Order order = ordersRepository.findById(storeId, orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return order;
    }

    String getStoreId () {
        return CustomSecurityContext.getStoreId();
    }

    String getUserId () {
        return CustomSecurityContext.getLoggedInUser()
                .map(user -> user.getAttributes().get("sub"))
                .map(Object::toString)
                .orElse(null);
    }

    boolean isSuperAdmin () {
        return CustomSecurityContext.hasRole("SUPER_ADMIN");
    }

    boolean isAdmin () {
        return CustomSecurityContext.hasRole("ADMIN");
    }
}

package pl.commercelink.web;

import org.springframework.beans.propertyeditors.CustomCollectionEditor;
import org.springframework.web.bind.WebDataBinder;

import java.util.List;

/** How the custom filter form binds its multi-value fields. */
public final class OrdersControllerBinding {

    private OrdersControllerBinding() {
    }

    /**
     * A ticked value arrives as its own parameter; without this, a single ticked marketplace whose name holds a comma
     * would be split into two values by the MVC conversion service.
     */
    public static void bindFilterForm(WebDataBinder binder) {
        for (String field : List.of("status", "shipmentType", "paymentSource", "sourceName")) {
            binder.registerCustomEditor(List.class, field, new CustomCollectionEditor(List.class));
        }
    }
}

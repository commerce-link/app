package pl.commercelink.web.deliveries.create;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.support.RequestContext;
import org.thymeleaf.spring6.context.webmvc.SpringWebMvcThymeleafRequestContext;
import org.thymeleaf.spring6.naming.SpringContextVariableNames;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.orders.*;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.dtos.SuggestedDeliveryItem;
import pl.commercelink.web.settings.SettingsTemplateRenderer;
import pl.commercelink.warehouse.RestockPriceCategory;

import java.util.*;
import java.util.regex.Pattern;

/** Renders the create-delivery pages as the controller would, with Spring's request context for th:field. */
final class DeliveryCreateTemplates {

    static final String ORDER_ID = "e2ed0004-dropship-0000-0000-000000000000";

    private DeliveryCreateTemplates() {
    }

    static String render(String template, Map<String, Object> variables) {
        MockServletContext servletContext = new MockServletContext();
        GenericWebApplicationContext applicationContext = new GenericWebApplicationContext(servletContext);
        applicationContext.refresh();
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        RequestContext requestContext = new RequestContext(request, new MockHttpServletResponse(), servletContext, variables);
        Map<String, Object> bound = new HashMap<>(variables);
        bound.put(SpringContextVariableNames.SPRING_REQUEST_CONTEXT, requestContext);
        bound.put(SpringContextVariableNames.THYMELEAF_REQUEST_CONTEXT,
                new SpringWebMvcThymeleafRequestContext(requestContext, request));
        bound.putIfAbsent("navigation", null);
        String html = SettingsTemplateRenderer.render(template, bound);
        int start = html.indexOf("<section class=\"cl-page\"");
        return html.substring(start, html.indexOf("</main>", start));
    }

    static String fragment(String selector, Map<String, Object> variables) {
        return SettingsTemplateRenderer.render("<div th:replace=\"~{" + selector + "}\"></div>", variables);
    }

    static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    /** Names of every field the page would post (inputs, selects, textareas; disabled ones included). */
    static List<String> fieldNames(String html) {
        List<String> names = new ArrayList<>();
        var matcher = Pattern.compile("<(?:input|select|textarea)[^>]*\\sname=\"([^\"]+)\"").matcher(html);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    static Order order(boolean pickupPoint) {
        Order order = new Order("store-1");
        order.setOrderId(ORDER_ID);
        order.setStatus(OrderStatus.Assembly);
        order.setFulfilmentType(FulfilmentType.DirectToConsumer);
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Jan");
        shipping.setSurname("Kowalski");
        shipping.setStreetAndNumber("ul. Polna 1");
        shipping.setPostalCode("00-001");
        shipping.setCity("Warszawa");
        shipping.setCountry("PL");
        shipping.setPhone("+48601234567");
        shipping.setEmail("jan.kowalski@example.pl");
        order.setShippingDetails(shipping);
        BillingDetails billing = new BillingDetails();
        billing.setEmail("jan.kowalski@example.pl");
        order.setBillingDetails(billing);
        if (pickupPoint) {
            Shipment shipment = new Shipment(ShipmentType.PickupPoint);
            shipment.setCarrier("InPost");
            shipment.setCollectionPointCode("WAW04A");
            order.addShipment(shipment);
        }
        return order;
    }

    /** A warehouse form: one product with an order source (2 pcs, ticked) and a restock source (1 pc, unticked). */
    static DeliveryCreationForm warehouseForm() {
        Order order = order(false);
        OrderItem cpu = new OrderItem(order.getOrderId(), "CPU", "AMD Ryzen 7 9800X3D", 2, 749, "100-100001084WOF", false, 0);
        cpu.setStatus(FulfilmentStatus.Allocation);
        cpu.setManufacturerCode("100-100001084WOF");
        cpu.setEan("5901234123457");
        cpu.setCost(579.5);
        cpu.setDeliveryId("Acme");
        Allocation fromOrder = Allocation.fromOrderItem(order, cpu);
        fromOrder.setSelected(true);
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setStoreId("store-1");
        form.setProvider("Acme");
        form.setTax(1.23);
        form.setItems(new ArrayList<>(DeliveryItem.groupAndUnify(List.of(fromOrder))));
        SuggestedDeliveryItem suggestion = new SuggestedDeliveryItem();
        suggestion.setName("Kingston FURY Beast 16GB");
        suggestion.setEan("5900000000011");
        suggestion.setMfn("MFN-FURY-16");
        suggestion.setUnitCost(189);
        suggestion.setExpectedQty(4);
        suggestion.setPriceCategory(RestockPriceCategory.LowestPrice);
        form.setSuggestedItems(new ArrayList<>(List.of(suggestion)));
        return form;
    }

    /** A dropship form of one order line (2 pcs, ticked). */
    static DeliveryCreationForm dropshipForm() {
        DeliveryCreationForm form = warehouseForm();
        form.setSuggestedItems(new ArrayList<>());
        return form;
    }

    static DeliveryCreatePage warehousePage(boolean superAdmin, boolean purchaseAvailable, boolean approval) {
        return new DeliveryCreatePage(DeliveryCreateLinks.of(superAdmin, "store-1", "Acme", null, null), "Acme", "Acme",
                null, null, null, purchaseAvailable, null, approval, true, 1, false);
    }

    static DeliveryCreatePage dropshipPage(boolean fromOrder, String blockedReason, boolean pickupPoint) {
        Order order = order(pickupPoint);
        return new DeliveryCreatePage(DeliveryCreateLinks.of(false, "store-1", "Acme", ORDER_ID, fromOrder ? "order" : null),
                "Acme", "Acme", order, order.getShippingDetails(),
                order.firstShipment().filter(s -> s.getType() == ShipmentType.PickupPoint).orElse(null),
                true, blockedReason, false, false, 1, false);
    }

    static Map<String, Object> model(DeliveryCreatePage page, DeliveryCreationForm form) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("page", page);
        variables.put("form", form);
        return variables;
    }
}

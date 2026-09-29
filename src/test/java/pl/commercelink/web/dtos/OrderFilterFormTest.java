package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.ServletRequestDataBinder;
import pl.commercelink.orders.filters.OrderFilterField;
import pl.commercelink.orders.filters.model.OrderFilterCondition;
import pl.commercelink.web.OrdersControllerBinding;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFilterFormTest {

    private static OrderFilterForm bind(MockHttpServletRequest request) {
        OrderFilterForm form = new OrderFilterForm();
        ServletRequestDataBinder binder = new ServletRequestDataBinder(form, "orderFilterForm");
        // Spring MVC binds with a conversion service, which splits a lone "A, B" into two list elements
        binder.setConversionService(new DefaultFormattingConversionService());
        OrdersControllerBinding.bindFilterForm(binder);
        binder.bind(request);
        return form;
    }

    @Test
    void everyTickedValueBecomesItsOwnCondition() {
        // given
        OrderFilterForm form = new OrderFilterForm();
        form.setLabel("Allegro lub Ceneo");
        form.setStatus(List.of("New", "Blocked"));
        form.setSourceName(List.of("Allegro", "Ceneo"));
        form.setShippingDue("DueToday");

        // when
        List<OrderFilterCondition> conditions = form.toConditions();

        // then
        assertThat(conditions).containsExactly(
                OrderFilterCondition.of(OrderFilterField.Status, "New"),
                OrderFilterCondition.of(OrderFilterField.Status, "Blocked"),
                OrderFilterCondition.of(OrderFilterField.ShippingDue, "DueToday"),
                OrderFilterCondition.of(OrderFilterField.SourceName, "Allegro"),
                OrderFilterCondition.of(OrderFilterField.SourceName, "Ceneo"));
    }

    @Test
    void repeatedParametersBindAsAList() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/dashboard/orders/filters");
        request.addParameter("status", "New", "Blocked");
        request.addParameter("paymentSource", "CashOnDelivery");

        // when
        OrderFilterForm form = bind(request);

        // then
        assertThat(form.getStatus()).containsExactly("New", "Blocked");
        assertThat(form.getPaymentSource()).containsExactly("CashOnDelivery");
        assertThat(form.getShipmentType()).isEmpty();
    }

    @Test
    void aLoneMarketplaceNameWithACommaStaysOneValue() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/dashboard/orders/filters");
        request.addParameter("sourceName", "Sklep Kowalski, Nowak");

        // when
        OrderFilterForm form = bind(request);

        // then
        assertThat(form.getSourceName()).containsExactly("Sklep Kowalski, Nowak");
    }

    @Test
    void nothingTickedMeansNoConditionForThatField() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/dashboard/orders/filters");
        request.addParameter("label", "Pusty");

        // when
        OrderFilterForm form = bind(request);

        // then
        assertThat(form.toConditions()).isEmpty();
    }
}

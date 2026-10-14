package pl.commercelink.web.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.rma.RmaCreateService;
import pl.commercelink.orders.rma.RmaOrderSearch;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.dtos.RmaCreateForm;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RmaCreateControllerTest {

    private static final String STORE = "store-1";
    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private RmaOrderSearch search;
    @Mock
    private RmaNewPageFactory pages;
    @Mock
    private RmaCreateService service;
    @Mock
    private OrdersRepository orders;
    @Mock
    private MessageSource messages;
    @Mock
    private RedirectAttributes redirect;
    @InjectMocks
    private RmaCreateController controller;

    private static <T> T asStore(java.util.function.Supplier<T> call) {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE);
            return call.get();
        }
    }

    @Test
    void oldParametersRedirectToTheSearch() {
        // when
        String byOrder = asStore(() -> controller.show(null, null, "abc-1", null, null, null, new ExtendedModelMap(), PL));
        String bySerial = asStore(() -> controller.show(null, "order", null, "SN 1/2", null, null, new ExtendedModelMap(), PL));
        String byEmail = asStore(() -> controller.show(null, null, null, null, "a+b@x.pl", null, new ExtendedModelMap(), PL));

        // then
        assertThat(byOrder).isEqualTo("redirect:/dashboard/rma/new?q=abc-1");
        assertThat(bySerial).isEqualTo("redirect:/dashboard/rma/new?q=SN%201%2F2&from=order");
        assertThat(byEmail).isEqualTo("redirect:/dashboard/rma/new?q=a%2Bb%40x.pl");
    }

    @Test
    void theSearchIsRenderedThroughThePageFactory() {
        // given
        RmaOrderSearch.Result result = new RmaOrderSearch.Result(RmaOrderSearch.Kind.EMPTY, null, false, null, null, List.of(), false);
        RmaNewPage page = mock(RmaNewPage.class);
        when(search.search(STORE, "x")).thenReturn(result);
        when(pages.build(STORE, result, "order", null, PL)).thenReturn(page);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = asStore(() -> controller.show("x", "order", null, null, null, null, model, PL));

        // then
        assertThat(view).isEqualTo("rma-new");
        assertThat(model.get("page")).isSameAs(page);
    }

    @Test
    void aCreatedRmaRedirectsToItsPage() {
        // given
        RmaCreateForm form = new RmaCreateForm();
        when(service.create(STORE, form)).thenReturn(new RmaCreateService.Outcome("rma-9", null, List.of()));
        when(messages.getMessage("rma.create.success", null, PL)).thenReturn("ok");

        // when
        String view = asStore(() -> controller.create(form, new ExtendedModelMap(), redirect, PL));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/rma-9");
        verify(redirect).addFlashAttribute("successMessage", "ok");
    }

    @Test
    void aRejectedSubmitRendersThePageWithWhatWasTyped() {
        // given
        RmaCreateForm form = new RmaCreateForm();
        form.setOrderId("o-1");
        Order order = new Order(STORE);
        order.setStatus(pl.commercelink.orders.OrderStatus.Delivered);
        RmaCreateService.Outcome outcome = new RmaCreateService.Outcome(null, null,
                List.of(new RmaCreateForm.Error("email", "rma.new.error.email.invalid", new Object[0], null)));
        RmaNewPage page = mock(RmaNewPage.class);
        when(service.create(STORE, form)).thenReturn(outcome);
        when(orders.findById(STORE, "o-1")).thenReturn(order);
        when(pages.rebuild(STORE, order, form, outcome, PL)).thenReturn(page);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = asStore(() -> controller.create(form, model, redirect, PL));

        // then
        assertThat(view).isEqualTo("rma-new");
        assertThat(model.get("page")).isSameAs(page);
        verify(redirect, never()).addFlashAttribute(anyString(), any());
    }

    @Test
    void aSubmitForAnOrderThatIsGoneRendersTheSearchWithThePageError() {
        // given
        RmaCreateForm form = new RmaCreateForm();
        form.setOrderId("o-1");
        form.setFrom("order");
        RmaOrderSearch.Result result = new RmaOrderSearch.Result(RmaOrderSearch.Kind.NOT_FOUND, "o-1", false, null, null, List.of(), false);
        RmaNewPage page = mock(RmaNewPage.class);
        RmaNewPage withError = mock(RmaNewPage.class);
        when(service.create(STORE, form)).thenReturn(new RmaCreateService.Outcome(null, RmaCreateService.ORDER_GONE, List.of()));
        when(orders.findById(STORE, "o-1")).thenReturn(null);
        when(search.search(STORE, "o-1")).thenReturn(result);
        when(pages.build(STORE, result, "order", PL)).thenReturn(page);
        when(messages.getMessage(RmaCreateService.ORDER_GONE, null, PL)).thenReturn("gone");
        when(page.withPageError("gone")).thenReturn(withError);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = asStore(() -> controller.create(form, model, redirect, PL));

        // then
        assertThat(view).isEqualTo("rma-new");
        assertThat(model.get("page")).isSameAs(withError);
    }

    @Test
    void fieldErrorsForAnOrderThatTurnedIneligibleMeanwhileShowTheOrderGoneError() {
        // given
        RmaCreateForm form = new RmaCreateForm();
        form.setOrderId("o-1");
        Order order = new Order(STORE);
        order.setStatus(pl.commercelink.orders.OrderStatus.New);
        RmaCreateService.Outcome outcome = new RmaCreateService.Outcome(null, null,
                List.of(new RmaCreateForm.Error("email", "rma.new.error.email.invalid", new Object[0], null)));
        RmaOrderSearch.Result result = new RmaOrderSearch.Result(RmaOrderSearch.Kind.ORDER, "o-1", false, order, null, List.of(), false);
        RmaNewPage page = mock(RmaNewPage.class);
        RmaNewPage withError = mock(RmaNewPage.class);
        when(service.create(STORE, form)).thenReturn(outcome);
        when(orders.findById(STORE, "o-1")).thenReturn(order);
        when(search.search(STORE, "o-1")).thenReturn(result);
        when(pages.build(STORE, result, null, PL)).thenReturn(page);
        when(messages.getMessage(RmaCreateService.ORDER_GONE, null, PL)).thenReturn("gone");
        when(page.withPageError("gone")).thenReturn(withError);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = asStore(() -> controller.create(form, model, redirect, PL));

        // then
        assertThat(view).isEqualTo("rma-new");
        assertThat(model.get("page")).isSameAs(withError);
        verify(pages, never()).rebuild(any(), any(), any(), any(), any());
    }

    @Test
    void theFormBindsOnlyItsOwnFields() {
        // given
        WebDataBinder binder = new WebDataBinder(new RmaCreateForm());

        // when
        controller.binder(binder);

        // then
        assertThat(binder.getAllowedFields()).containsExactlyInAnyOrder("orderId", "email", "from", "items*");
    }

    @Test
    void anOrderChosenFromASerialListPassesTheSerialToThePage() {
        // given
        RmaOrderSearch.Result result = new RmaOrderSearch.Result(RmaOrderSearch.Kind.EMPTY, null, false, null, null, List.of(), false);
        RmaNewPage page = mock(RmaNewPage.class);
        when(search.search(STORE, "abc-1")).thenReturn(result);
        when(pages.build(STORE, result, null, "SN-1", PL)).thenReturn(page);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        asStore(() -> controller.show("abc-1", null, null, null, null, "SN-1", model, PL));

        // then
        assertThat(model.get("page")).isSameAs(page);
    }

    @Test
    void aCraftedItemIndexIsABadRequest() throws Exception {
        // given
        jakarta.servlet.http.HttpServletResponse response = mock(jakarta.servlet.http.HttpServletResponse.class);

        // when
        controller.refuseCraftedIndex(response);

        // then
        verify(response).sendError(400);
    }

    @Test
    void anOverlongOrderIdIsNotReadAfterARejectedSubmit() {
        // given
        String overlong = "e".repeat(2000);
        RmaCreateForm form = new RmaCreateForm();
        form.setOrderId(overlong);
        when(service.create(STORE, form)).thenReturn(new RmaCreateService.Outcome(null, RmaCreateService.ORDER_GONE, List.of()));
        RmaOrderSearch.Result notFound = new RmaOrderSearch.Result(RmaOrderSearch.Kind.NOT_FOUND, overlong, false, null, null, List.of(), false);
        when(search.search(STORE, overlong)).thenReturn(notFound);
        when(pages.build(STORE, notFound, null, PL)).thenReturn(mock(RmaNewPage.class));

        // when
        asStore(() -> controller.create(form, new ExtendedModelMap(), redirect, PL));

        // then
        verify(orders, never()).findById(any(), any());
    }
}

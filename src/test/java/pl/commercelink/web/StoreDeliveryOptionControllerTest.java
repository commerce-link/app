package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DeliveryOptionForm;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreDeliveryOptionControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreDeliveryOptionController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aStoreWithoutCheckoutSettingsGetsItsFirstOption() {
        // given
        Store store = store("store-1");

        // when
        String view = controller.createOption(form("Kurier", "19,99"), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getCheckoutConfiguration().getActiveDeliveryOptions()).singleElement()
                .satisfies(option -> assertThat(option.getPrice()).isEqualTo(19.99));
        assertThat(view).isEqualTo("redirect:/dashboard/store/payments");
    }

    @Test
    void anInvalidOptionIsRejectedAtTheFieldWithoutSaving() {
        // given
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.createOption(form("", "19,99"), "fetch", model, PL, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), response);

        // then
        verify(storesRepository, never()).save(any(Store.class));
        assertThat(view).isEqualTo("store-delivery-option :: optionForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of("name", "store.payments.delivery.name.required"));
    }

    /** Offers and paid baskets look the option up by id: deleting it lost the order after payment. */
    @Test
    void removingAnOptionRetiresItSoBasketsThatChoseItStillFindIt() {
        // given
        Store store = store("store-1");
        DeliveryOption courier = option(store, "Kurier");

        // when
        controller.confirmDelete(courier.getId(), new ExtendedModelMap(), PL);
        String view = controller.deleteOption(courier.getId(), PL, new RedirectAttributesModelMap());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getCheckoutConfiguration().getActiveDeliveryOptions()).isEmpty();
        assertThat(store.getCheckoutConfiguration().findDeliveryOption(courier.getId())).isSameAs(courier);
        assertThat(view).isEqualTo("redirect:/dashboard/store/payments");
    }

    @Test
    void editingKeepsTheIdSoOffersFollowTheChange() {
        // given
        Store store = store("store-1");
        DeliveryOption courier = option(store, "Kurier");
        String id = courier.getId();

        // when
        controller.updateOption(id, form("Kurier DPD", "24"), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        assertThat(store.getCheckoutConfiguration().findDeliveryOption(id).getName()).isEqualTo("Kurier DPD");
        assertThat(store.getCheckoutConfiguration().findDeliveryOption(id).getPrice()).isEqualTo(24.0);
    }

    @Test
    void anOptionOfAnotherStoreOrARetiredOneAnswers404OnEveryAction() {
        // given
        Store store = store("store-1");
        DeliveryOption old = option(store, "Stary");
        store.getCheckoutConfiguration().retireDeliveryOption(old.getId());
        List<Consumer<String>> calls = List.of(
                id -> controller.editOption(id, new ExtendedModelMap(), PL),
                id -> controller.updateOption(id, form("X", "1"), null, new ExtendedModelMap(), PL,
                        new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse()),
                id -> controller.confirmDelete(id, new ExtendedModelMap(), PL));

        // when / then
        for (String id : List.of("foreign", old.getId())) {
            calls.forEach(call -> assertThatThrownBy(() -> call.accept(id)).isInstanceOf(ResponseStatusException.class));
        }
        verify(storesRepository, never()).save(any(Store.class));
    }

    @Test
    void superAdminAddsToTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        Store store = store("store-2");

        // when
        String view = controller.superAdminCreateOption("store-2", form("Kurier", "10"), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        assertThat(store.getCheckoutConfiguration().getActiveDeliveryOptions()).hasSize(1);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/payments");
    }

    private static DeliveryOptionForm form(String name, String price) {
        DeliveryOptionForm form = new DeliveryOptionForm();
        form.setName(name);
        form.setPrice(price);
        form.setType(ShipmentType.Courier.name());
        return form;
    }

    private static DeliveryOption option(Store store, String name) {
        CheckoutConfiguration configuration = store.getCheckoutConfiguration() != null
                ? store.getCheckoutConfiguration() : new CheckoutConfiguration();
        DeliveryOption option = new DeliveryOption();
        option.setName(name);
        configuration.addDeliveryOption(option);
        store.setCheckoutConfiguration(configuration);
        return option;
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private void authenticateAs(String storeId, String role) {
        Map<String, String> attributes = storeId != null
                ? Map.of("storeId", storeId, "role", role)
                : Map.of("role", role);
        CustomUser user = new CustomUser(null, null, attributes);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}

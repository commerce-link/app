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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.WarehouseAddressForm;
import pl.commercelink.web.settings.ConfirmAction;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.testsupport.SecurityContextLogin.logInAs;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreWarehouseAddressControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreWarehouseAddressController controller;

    @BeforeEach
    void messagesAreTheirKeys() {
        when(messageSource.getMessage(anyString(), any(), eq(POLISH))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Store store(String storeId, ShippingDetails... addresses) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.setShippingDetails(new LinkedList<>(List.of(addresses)));
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private ShippingDetails address(String id, boolean isDefault) {
        ShippingDetails details = validForm().toNewShippingDetails();
        details.setId(id);
        details.set_default(isDefault);
        return details;
    }

    private WarehouseAddressForm validForm() {
        WarehouseAddressForm form = new WarehouseAddressForm();
        form.setCompanyName("Oddział Berlin");
        form.setStreetAndNumber("Lagerstraße 3");
        form.setPostalCode("10115");
        form.setCity("Berlin");
        form.setCountry("DE");
        form.setEmail("berlin@sklep.pl");
        form.setPhone("+49 30 123456");
        return form;
    }

    private MockHttpServletRequest requestWithFlashSupport() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> errors(ExtendedModelMap model) {
        return (Map<String, String>) model.get("errors");
    }

    @Test
    void aNewAddressIsAddedToTheStoreFromTheSessionAndTheListSaysSo() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.createAddress(validForm(), null, new ExtendedModelMap(), POLISH, redirect,
                new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getShippingDetails()).singleElement().satisfies(details -> {
            assertThat(details.getCompanyName()).isEqualTo("Oddział Berlin");
            assertThat(details.getId()).isNotBlank();
            assertThat(details.is_default()).isTrue();
        });
        assertThat(view).isEqualTo("redirect:/dashboard/store/warehouse");
        assertThat(redirect.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("store.warehouse.address.added");
    }

    @Test
    void anIncompleteAddressIsNeverSavedSilently() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        WarehouseAddressForm form = validForm();
        form.setPostalCode("");
        form.setCity(null);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.createAddress(form, null, model, POLISH, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(storesRepository, never()).save(any());
        assertThat(view).isEqualTo("store-warehouse-address");
        assertThat(errors(model)).containsOnlyKeys("postalCode", "city");
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/warehouse/addresses/new");
    }

    @Test
    void asyncCreateAnswersUnprocessableWithTheFormWhenInvalid() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.createAddress(new WarehouseAddressForm(), "fetch", new ExtendedModelMap(), POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), response);

        // then
        assertThat(view).isEqualTo("store-warehouse-address :: addressForm");
        assertThat(response.getStatus()).isEqualTo(422);
    }

    @Test
    void asyncCreateTellsTheScriptToReturnToTheListWithTheMessageWaitingThere() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        MockHttpServletRequest request = requestWithFlashSupport();
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.createAddress(validForm(), "fetch", model, POLISH, new RedirectAttributesModelMap(), request, response);

        // then
        assertThat(view).isEqualTo("store-warehouse-address :: addressForm");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(model.get("redirectTo")).isEqualTo("/dashboard/store/warehouse");
        FlashMap flash = (FlashMap) request.getAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE);
        assertThat(flash.get("settingsSavedMessage")).isEqualTo("store.warehouse.address.added");
        assertThat(flash.getTargetRequestPath()).isEqualTo("/dashboard/store/warehouse");
    }

    @Test
    void editingKeepsTheAddressIdAndCanMakeItTheDefault() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1", address("a-1", true), address("a-2", false));
        WarehouseAddressForm form = validForm();
        form.setCity("Hamburg");
        form.setMakeDefault(true);

        // when
        String view = controller.updateAddress("a-2", form, null, new ExtendedModelMap(), POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.findShippingDetails("a-2").orElseThrow().getCity()).isEqualTo("Hamburg");
        assertThat(store.getDefaultShippingDetails().getId()).isEqualTo("a-2");
        assertThat(view).isEqualTo("redirect:/dashboard/store/warehouse");
    }

    @Test
    void theEditPageOfTheDefaultAddressSaysItIsAlreadyTheDefault() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", address("a-1", true));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.editAddress("a-1", model, POLISH);

        // then
        assertThat(view).isEqualTo("store-warehouse-address");
        assertThat(model.get("alreadyDefault")).isEqualTo(true);
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/warehouse/addresses/a-1");
    }

    @Test
    void anAddressOfAnotherStoreIsNotFound() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", address("a-1", true));
        store("other-store", address("foreign", true));

        // when / then
        assertThatThrownBy(() -> controller.editAddress("foreign", new ExtendedModelMap(), POLISH))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.makeDefault("foreign", POLISH, new RedirectAttributesModelMap()))
                .isInstanceOf(ResponseStatusException.class);
        verify(storesRepository, never()).save(any());
    }

    @Test
    void settingTheDefaultReturnsToTheList() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1", address("a-1", true), address("a-2", false));

        // when
        String view = controller.makeDefault("a-2", POLISH, new RedirectAttributesModelMap());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getDefaultShippingDetails().getId()).isEqualTo("a-2");
        assertThat(store.findShippingDetails("a-1").orElseThrow().is_default()).isFalse();
        assertThat(view).isEqualTo("redirect:/dashboard/store/warehouse");
    }

    @Test
    void withoutJavaScriptDeletingAsksOnAConfirmationPage() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", address("a-1", true));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.confirmDelete("a-1", model, POLISH);

        // then
        assertThat(view).isEqualTo("settings-confirm");
        ConfirmAction confirm = (ConfirmAction) model.get("confirm");
        assertThat(confirm.actionPath()).isEqualTo("/dashboard/store/warehouse/addresses/a-1/delete");
        assertThat(confirm.cancelPath()).isEqualTo("/dashboard/store/warehouse");
    }

    @Test
    void deletingTheDefaultAddressPassesTheDefaultToTheNextOne() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1", address("a-1", true), address("a-2", false));

        // when
        controller.deleteAddress("a-1", POLISH, new RedirectAttributesModelMap());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getShippingDetails()).extracting(ShippingDetails::getId).containsExactly("a-2");
        assertThat(store.getDefaultShippingDetails().getId()).isEqualTo("a-2");
    }

    @Test
    void superAdminManagesTheAddressesOfTheStoreFromThePath() {
        // given
        logInAs("SUPER_ADMIN", "none");
        Store store = store("store-9");

        // when
        String view = controller.superAdminCreateAddress("store-9", validForm(), null, new ExtendedModelMap(), POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-9/warehouse");
    }
}

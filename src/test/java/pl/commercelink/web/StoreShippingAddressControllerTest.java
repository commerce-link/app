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
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.LabelSenderForm;
import pl.commercelink.web.dtos.ShippingAddressForm;

import java.util.Locale;

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
class StoreShippingAddressControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreShippingAddressController controller;

    @BeforeEach
    void messagesAreTheirKeys() {
        when(messageSource.getMessage(anyString(), any(), eq(POLISH))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.setShippingConfiguration(new ShippingConfiguration());
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private static ShippingAddressForm validForm() {
        ShippingAddressForm form = ShippingAddressForm.empty();
        form.setCompanyName("Demo Store");
        form.setStreetAndNumber("Marszałkowska 1");
        form.setPostalCode("00-001");
        form.setCity("Warszawa");
        form.setEmail("magazyn@sklep.pl");
        form.setPhone("501234567");
        return form;
    }

    private static ShippingDetails address(String id) {
        ShippingDetails details = validForm().toNewShippingDetails();
        details.setId(id);
        return details;
    }

    private static MockHttpServletRequest requestWithFlashSupport() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
        return request;
    }

    @Test
    void theFirstPickupAddressOfTheStoreFromTheSessionBecomesItsDefault() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");

        // when
        String view = controller.createAddress(validForm(), null, new ExtendedModelMap(), POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getShippingConfiguration().getPickUpAddresses()).singleElement()
                .satisfies(address -> assertThat(address.is_default()).isTrue());
        assertThat(view).isEqualTo("redirect:/dashboard/store/shipping");
    }

    @Test
    void anAddressWithoutAPhoneIsNotSavedAndTheAsyncFormGetsItsErrors() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        ShippingAddressForm form = validForm();
        form.setPhone("");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.createAddress(form, "fetch", new ExtendedModelMap(), POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), response);

        // then
        verify(storesRepository, never()).save(any());
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(view).isEqualTo("store-shipping-address :: addressForm");
        assertThat(store.getShippingConfiguration().getPickUpAddresses()).isEmpty();
    }

    @Test
    void anAddressOfAnotherStoreIsNotFound() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");

        // when / then
        assertThatThrownBy(() -> controller.editAddress("elsewhere", new ExtendedModelMap(), POLISH))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void superAdminDeletesTheDefaultAddressOfTheStoreInThePathAndTheNextOneTakesOver() {
        // given
        logInAs("SUPER_ADMIN", "own-store");
        Store store = store("store-7");
        store.getShippingConfiguration().addPickUpAddress(address("a"), false);
        store.getShippingConfiguration().addPickUpAddress(address("b"), false);

        // when
        String view = controller.superAdminDeleteAddress("store-7", "a", POLISH, new RedirectAttributesModelMap());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getDefaultPickupAddress()).map(ShippingDetails::getId).contains("b");
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-7/shipping");
    }

    @Test
    void choosingThePickupAddressAsTheSenderDropsTheSavedSender() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        store.getShippingConfiguration().setLabelSender(address("head-office"));
        LabelSenderForm form = LabelSenderForm.from(null);

        // when
        controller.saveSender(form, "fetch", new ExtendedModelMap(), POLISH, new RedirectAttributesModelMap(),
                requestWithFlashSupport(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getShippingConfiguration().getSenderAddresses()).isEmpty();
    }

    @Test
    void otherSenderDetailsAreSavedAsTheOnlySender() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        LabelSenderForm form = LabelSenderForm.from(address("x"));
        form.setCompanyName("Centrala");

        // when
        controller.saveSender(form, null, new ExtendedModelMap(), POLISH, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        assertThat(store.getShippingConfiguration().getLabelSender().getCompanyName()).isEqualTo("Centrala");
        assertThat(store.getShippingConfiguration().getSenderAddresses()).hasSize(1);
    }
}

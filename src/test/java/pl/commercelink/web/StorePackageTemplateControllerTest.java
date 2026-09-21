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
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.PackageTemplateForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.testsupport.SecurityContextLogin.logInAs;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorePackageTemplateControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StorePackageTemplateController controller;

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

    private static PackageTemplateForm form(String name, String width) {
        PackageTemplateForm.ParcelRow row = new PackageTemplateForm.ParcelRow();
        row.setWidth(width);
        row.setDepth("30");
        row.setHeight("20");
        row.setWeight("5");
        row.setDescription("Karton");
        PackageTemplateForm form = new PackageTemplateForm();
        form.setName(name);
        form.setParcels(new ArrayList<>(List.of(row)));
        return form;
    }

    @Test
    void theFirstTemplateOfTheStoreBecomesItsDefault() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");

        // when
        String view = controller.createTemplate(form("Karton M", "40"), null, new ExtendedModelMap(), POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getShippingConfiguration().getPackageTemplates()).singleElement().satisfies(template -> {
            assertThat(template.getName()).isEqualTo("Karton M");
            assertThat(template.isDefault()).isTrue();
            assertThat(template.getParcels()).singleElement().satisfies(parcel -> assertThat(parcel.getWidth()).isEqualTo(40));
        });
        assertThat(view).isEqualTo("redirect:/dashboard/store/shipping");
    }

    @Test
    void anInvalidTemplateIsNotSavedAndTheSummaryNamesTheParcel() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.createTemplate(form("Karton M", "abc"), "fetch", model, POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), response);

        // then
        verify(storesRepository, never()).save(any());
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(view).isEqualTo("store-shipping-template :: templateForm");
        assertThat(model.get("errors")).isEqualTo(Map.of("parcel-0-width", "store.shipping.template.number.invalid"));
        assertThat(model.get("errorLabels")).asInstanceOf(MAP)
                .containsEntry("parcel-0-width", "store.shipping.template.parcel.field");
    }

    @Test
    void superAdminEditsATemplateOfTheStoreInThePath() {
        // given
        logInAs("SUPER_ADMIN", "own-store");
        Store store = store("store-7");
        PackageTemplate template = form("Stary", "10").toNewTemplate();
        template.setId("t1");
        store.getShippingConfiguration().addPackageTemplate(template, false);

        // when
        String view = controller.superAdminUpdateTemplate("store-7", "t1", form("Nowy", "50"), null, new ExtendedModelMap(),
                POLISH, new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        assertThat(template.getName()).isEqualTo("Nowy");
        assertThat(template.getParcels().getFirst().getWidth()).isEqualTo(50);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-7/shipping");
    }

    @Test
    void aTemplateOfAnotherStoreIsNotFound() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");

        // when / then
        assertThatThrownBy(() -> controller.editTemplate("elsewhere", new ExtendedModelMap(), POLISH))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}

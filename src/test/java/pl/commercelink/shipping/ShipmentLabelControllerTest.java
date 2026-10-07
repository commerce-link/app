package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.api.Label;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentLabelControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock private StoresRepository storesRepository;
    @Mock private ShippingService shippingService;
    @Mock private Store store;
    @Mock private ShippingProvider provider;

    private ShipmentLabelController controller;

    @BeforeEach
    void setUp() {
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(shippingService.providerName(store)).thenReturn("furgonetka");
        when(shippingService.supportsLabels(store, "furgonetka")).thenReturn(true);
        when(shippingService.providerFor(store)).thenReturn(provider);
        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        controller = new ShipmentLabelController(storesRepository, shippingService, messages) {
            @Override
            String storeId() {
                return "store-1";
            }
        };
    }

    @Test
    void theLabelIsSentAsAnAttachment() {
        // given
        when(provider.getLabel("21480003")).thenReturn(new Label(new byte[]{'%', 'P'}, "application/pdf", "etykieta-21480003.pdf"));

        // when
        Object result = controller.label("furgonetka", "21480003", "/dashboard/orders/o-1",
                new RedirectAttributesModelMap(), PL);

        // then
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;").contains("etykieta-21480003.pdf");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/pdf");
        assertThat((byte[]) response.getBody()).containsExactly('%', 'P');
    }

    @Test
    void aFileNameWithQuotesOrNonAsciiCharactersCannotBreakTheHeader() {
        // given
        when(provider.getLabel("1")).thenReturn(new Label(new byte[]{1}, "application/pdf", "zwrot \"ż\".pdf"));

        // when
        ResponseEntity<?> response = (ResponseEntity<?>) controller.label("furgonetka", "1", null,
                new RedirectAttributesModelMap(), PL);

        // then: the encoded filename* form carries the name, never a raw quote closing the parameter
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).contains("filename*=UTF-8''").doesNotContain("\"ż\"");
    }

    @Test
    void aProviderErrorGoesBackWithTheProvidersReason() {
        // given
        when(provider.getLabel("21480003")).thenThrow(new ShippingException("HTTP 404",
                new HttpClientException(404, "{\"errors\":[{\"message\":\"Brak etykiety\"}]}")));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        Object result = controller.label("furgonetka", "21480003", "/dashboard/orders/o-1", redirect, PL);

        // then
        assertThat(result).isEqualTo("redirect:/dashboard/orders/o-1");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("Brak etykiety");
    }

    @Test
    void anAdapterRefusalWithoutTheProvidersAnswerGoesBackWithOurOwnMessage() {
        // given: the adapter's English words (e.g. Furgonetka answered 204 with no label) are not shown to the operator
        when(provider.getLabel("21480003")).thenThrow(new ShippingException("Furgonetka has no label for package 21480003 (yet)"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        Object result = controller.label("furgonetka", "21480003", "/dashboard/rma/r-1", redirect, PL);

        // then
        assertThat(result).isEqualTo("redirect:/dashboard/rma/r-1");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("shipping.label.empty");
    }

    @Test
    void anEmptyLabelIsNeverSentAsAFile() {
        // given: a provider that hands back a label without bytes instead of refusing it
        when(provider.getLabel("21480003")).thenReturn(new Label(new byte[0], "application/pdf", "etykieta-21480003.pdf"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        Object result = controller.label("furgonetka", "21480003", "/dashboard/rma/r-1", redirect, PL);

        // then
        assertThat(result).isEqualTo("redirect:/dashboard/rma/r-1");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("shipping.label.empty");
    }

    @Test
    void aLabelWithoutContentIsNeverSentAsAFile() {
        // given
        when(provider.getLabel("21480003")).thenReturn(new Label(null, "application/pdf", "etykieta-21480003.pdf"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        Object result = controller.label("furgonetka", "21480003", "/dashboard/rma/r-1", redirect, PL);

        // then
        assertThat(result).isEqualTo("redirect:/dashboard/rma/r-1");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("shipping.label.empty");
    }

    @Test
    void aPackageWhoseLabelTheStoreCannotGetIsRefusedWithoutAskingForIt() {
        // given: another integration than the store's, one without labels or a disconnected one
        when(shippingService.supportsLabels(store, "allegro")).thenReturn(false);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        Object result = controller.label("allegro", "1", "/dashboard/notifications", redirect, PL);

        // then
        assertThat(result).isEqualTo("redirect:/dashboard/notifications");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("shipping.label.unavailable");
        verifyNoInteractions(provider);
    }

    @Test
    void aBackAddressOutsideTheDashboardFallsBackToTheOrderList() {
        // given
        when(provider.getLabel("1")).thenThrow(new ShippingException("x"));

        // when
        Object result = controller.label("furgonetka", "1", "https://evil.example/", new RedirectAttributesModelMap(), PL);

        // then
        assertThat(result).isEqualTo("redirect:/dashboard/orders");
    }
}

package pl.commercelink.web.errorpage;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.publicpage.PublicPage;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicErrorViewResolverTest {

    @Mock private StoresRepository storesRepository;
    @Mock private ErrorViewResolver springDefault;

    @InjectMocks
    private PublicErrorViewResolver resolver;

    @Test
    void aMissingCustomerPageOfAKnownStoreGetsTheStoresErrorPageWith404() {
        // given
        when(storesRepository.findById("nova")).thenReturn(store());

        // when
        ModelAndView page = resolver.resolveErrorView(errorOf("/store/nova/client/offer/x/nie-ma", Locale.ENGLISH),
                HttpStatus.NOT_FOUND, Map.of("path", "/store/nova/client/offer/x/nie-ma", "error", "Not Found"));

        // then
        assertThat(page.getViewName()).isEqualTo("error/public");
        assertThat(page.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(page.getModel()).containsEntry("errorStatus", 404).containsEntry("notFound", true)
                .doesNotContainKeys("path", "error", "timestamp", "trace", "message");
        PublicPage publicPage = (PublicPage) page.getModel().get("publicPage");
        assertThat(publicPage.storeName()).isEqualTo("Nova Tech");
        assertThat(publicPage.language()).isEqualTo("en");
        assertThat(publicPage.otherLanguageHref()).isEqualTo("/store/nova/client/offer/x/nie-ma?lang=pl");
    }

    @Test
    void aFailureOnACustomerPageIsNotANotFound() {
        // given
        when(storesRepository.findById("nova")).thenReturn(store());

        // when
        ModelAndView page = resolver.resolveErrorView(errorOf("/store/nova/individual/offer/o1/data", Locale.ENGLISH),
                HttpStatus.BAD_REQUEST, Map.of());

        // then
        assertThat(page.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(page.getModel()).containsEntry("errorStatus", 400).containsEntry("notFound", false);
    }

    @Test
    void anUnknownStoreGetsTheFrameWithoutABrand() {
        // when
        ModelAndView page = resolver.resolveErrorView(errorOf("/store/nieznany/client/order/o1", Locale.ENGLISH),
                HttpStatus.INTERNAL_SERVER_ERROR, Map.of());

        // then
        PublicPage publicPage = (PublicPage) page.getModel().get("publicPage");
        assertThat(publicPage.storeId()).isNull();
        assertThat(publicPage.storeName()).isEmpty();
    }

    @Test
    void aStoreThatCannotBeReadGetsTheFrameWithoutABrand() {
        // given: the error may come from the database itself
        when(storesRepository.findById("nova")).thenThrow(new IllegalStateException("DynamoDB unavailable"));

        // when
        ModelAndView page = resolver.resolveErrorView(errorOf("/store/nova/client/order/o1", Locale.ENGLISH),
                HttpStatus.INTERNAL_SERVER_ERROR, Map.of());

        // then
        assertThat(((PublicPage) page.getModel().get("publicPage")).storeId()).isNull();
    }

    @Test
    void aPercentEncodedCustomerPathGetsTheStoresErrorPageToo() {
        // given: Spring Security and the controllers match the path decoded, so this is the store's offer page
        when(storesRepository.findById("nova")).thenReturn(store());

        // when
        ModelAndView page = resolver.resolveErrorView(errorOf("/store/nova/%63lient/offer/x/nie-ma", Locale.ENGLISH),
                HttpStatus.NOT_FOUND, Map.of());

        // then
        assertThat(page.getViewName()).isEqualTo("error/public");
        assertThat(((PublicPage) page.getModel().get("publicPage")).storeName()).isEqualTo("Nova Tech");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard/nie-ma", "/nie/ma", "/store/nova/settings", "/Store/nova/Catalog/x", "/store/nova",
            "/store//client/x"})
    void anyOtherPathIsLeftToSpringsDefaultViews(String path) {
        // given
        MockHttpServletRequest request = errorOf(path, Locale.ENGLISH);
        ModelAndView springPage = new ModelAndView("error/404");
        when(springDefault.resolveErrorView(request, HttpStatus.NOT_FOUND, Map.of())).thenReturn(springPage);

        // when
        ModelAndView page = resolver.resolveErrorView(request, HttpStatus.NOT_FOUND, Map.of());

        // then
        assertThat(page).isSameAs(springPage);
        verifyNoInteractions(storesRepository);
    }

    @Test
    void comesBeforeSpringsDefaultErrorViews() {
        // when / then
        assertThat(resolver.getOrder()).isLessThan(Ordered.LOWEST_PRECEDENCE);
    }

    private static MockHttpServletRequest errorOf(String path, Locale locale) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, path);
        request.addPreferredLocale(locale);
        return request;
    }

    private static Store store() {
        Store store = new Store();
        store.setStoreId("nova");
        store.setName("Nova Tech");
        return store;
    }
}

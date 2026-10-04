package pl.commercelink.starter.security.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.MappedInterceptor;
import pl.commercelink.starter.security.StoreAccessInterceptor;
import pl.commercelink.starter.security.StoreApiKeyAuthorizationInterceptor;
import pl.commercelink.registration.EmailVerificationInterceptor;
import pl.commercelink.starter.security.interceptor.ApiGatewayIdInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebConfigTest {

    private StoreAccessInterceptor storeAccessInterceptor;
    private InterceptorRegistry registry;

    @BeforeEach
    void setUp() {
        storeAccessInterceptor = new StoreAccessInterceptor();

        WebConfig webConfig = new WebConfig();
        ReflectionTestUtils.setField(webConfig, "cors", "http://localhost");
        ReflectionTestUtils.setField(webConfig, "apiGatewayIdInterceptor", mock(ApiGatewayIdInterceptor.class));
        ReflectionTestUtils.setField(webConfig, "storeApiKeyAuthorizationInterceptor", mock(StoreApiKeyAuthorizationInterceptor.class));
        ReflectionTestUtils.setField(webConfig, "storeAccessInterceptor", storeAccessInterceptor);
        ReflectionTestUtils.setField(webConfig, "emailVerificationInterceptor", mock(EmailVerificationInterceptor.class));

        registry = new InterceptorRegistry();
        WebMvcConfigurer configurer = webConfig.corsConfigurer();
        configurer.addInterceptors(registry);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET, /dashboard/store/categories",
            "POST, /dashboard/store/categories",
            "GET, /dashboard/store/fulfilment",
            "GET, /dashboard/store/receipts",
            "GET, /dashboard/store/receipts/system",
            "GET, /dashboard/store/receipts/system/disconnect",
            "GET, /dashboard/store/margins",
            "POST, /dashboard/store/margins"
    })
    void storeAccessInterceptorDoesNotGuardTheAdminStorePages(String method, String path) {
        // given / when / then
        assertThat(storeAccessGuards(new MockHttpServletRequest(method, path))).isFalse();
    }

    @Test
    void storeAccessInterceptorStillGuardsTheSuperAdminStoreCategoriesPage() {
        // given / when / then
        assertThat(storeAccessGuards(get("/dashboard/store/other-store/categories"))).isTrue();
    }

    @Test
    void storeAccessInterceptorStillGuardsTheSuperAdminStoreReceiptsPage() {
        // given / when / then
        assertThat(storeAccessGuards(get("/dashboard/store/other-store/receipts"))).isTrue();
    }

    private boolean storeAccessGuards(HttpServletRequest request) {
        ServletRequestPathUtils.parseAndCache(request);
        return mappedStoreAccessInterceptors().stream().anyMatch(i -> i.matches(request));
    }

    @SuppressWarnings("unchecked")
    private List<MappedInterceptor> mappedStoreAccessInterceptors() {
        List<Object> interceptors = (List<Object>) ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
        return interceptors.stream()
                .filter(MappedInterceptor.class::isInstance)
                .map(MappedInterceptor.class::cast)
                .filter(i -> i.getInterceptor() == storeAccessInterceptor)
                .toList();
    }

    private HttpServletRequest get(String path) {
        return new MockHttpServletRequest("GET", path);
    }
}

package pl.commercelink.starter.security.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.MappedInterceptor;
import pl.commercelink.starter.security.StoreAccessInterceptor;
import pl.commercelink.starter.security.StoreApiKeyAuthorizationInterceptor;
import pl.commercelink.registration.EmailVerificationInterceptor;
import pl.commercelink.starter.security.interceptor.ApiGatewayIdInterceptor;
import pl.commercelink.web.activity.DashboardReadOnlyInterceptor;
import pl.commercelink.web.activity.PublicStoreActivityInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class WebConfigTest {

    @Mock private ApiGatewayIdInterceptor apiGatewayIdInterceptor;
    @Mock private StoreApiKeyAuthorizationInterceptor storeApiKeyAuthorizationInterceptor;
    @Mock private EmailVerificationInterceptor emailVerificationInterceptor;
    @Mock private DashboardReadOnlyInterceptor dashboardReadOnlyInterceptor;
    @Mock private PublicStoreActivityInterceptor publicStoreActivityInterceptor;

    private final StoreAccessInterceptor storeAccessInterceptor = new StoreAccessInterceptor();
    private InterceptorRegistry registry;

    @BeforeEach
    void setUp() {
        WebConfig webConfig = new WebConfig(apiGatewayIdInterceptor, storeApiKeyAuthorizationInterceptor,
                storeAccessInterceptor, emailVerificationInterceptor, dashboardReadOnlyInterceptor,
                publicStoreActivityInterceptor);
        ReflectionTestUtils.setField(webConfig, "cors", "http://localhost");

        registry = new InterceptorRegistry();
        WebMvcConfigurer configurer = webConfig.corsConfigurer();
        configurer.addInterceptors(registry);
    }

    @Test
    void storeAccessInterceptorDoesNotGuardTheAdminStoreCategoriesPage() {
        // given / when / then
        assertThat(storeAccessGuards(get("/dashboard/store/categories"))).isFalse();
    }

    @Test
    void storeAccessInterceptorDoesNotGuardTheAdminStoreCategoriesUpdate() {
        // given / when / then
        assertThat(storeAccessGuards(post("/dashboard/store/categories"))).isFalse();
    }

    @Test
    void storeAccessInterceptorStillGuardsTheSuperAdminStoreCategoriesPage() {
        // given / when / then
        assertThat(storeAccessGuards(get("/dashboard/store/other-store/categories"))).isTrue();
    }

    @Test
    void storeAccessInterceptorDoesNotGuardTheAdminStoreFulfilmentPage() {
        // given / when / then
        assertThat(storeAccessGuards(get("/dashboard/store/fulfilment"))).isFalse();
    }

    @Test
    void storeAccessInterceptorDoesNotGuardTheAdminStoreReceiptsPage() {
        // given / when / then
        assertThat(storeAccessGuards(get("/dashboard/store/receipts"))).isFalse();
    }

    @Test
    void storeAccessInterceptorDoesNotGuardTheAdminStoreReceiptSystemPage() {
        // given / when / then
        assertThat(storeAccessGuards(get("/dashboard/store/receipts/system"))).isFalse();
    }

    @Test
    void storeAccessInterceptorDoesNotGuardTheAdminStoreReceiptSystemDisconnect() {
        // given / when / then
        assertThat(storeAccessGuards(get("/dashboard/store/receipts/system/disconnect"))).isFalse();
    }

    @Test
    void storeAccessInterceptorStillGuardsTheSuperAdminStoreReceiptsPage() {
        // given / when / then
        assertThat(storeAccessGuards(get("/dashboard/store/other-store/receipts"))).isTrue();
    }

    @Test
    void readOnlyGateGuardsEveryDashboardPage() {
        // given / when / then
        assertThat(guards(dashboardReadOnlyInterceptor, get("/dashboard"))).isTrue();
        assertThat(guards(dashboardReadOnlyInterceptor, get("/dashboard/orders"))).isTrue();
        assertThat(guards(dashboardReadOnlyInterceptor, post("/dashboard/store/branding"))).isTrue();
        assertThat(guards(dashboardReadOnlyInterceptor, get("/dashboard/store/other-store/receipts"))).isTrue();
    }

    @Test
    void readOnlyGateLeavesPagesOutsideTheDashboardAlone() {
        // given / when / then
        assertThat(guards(dashboardReadOnlyInterceptor, get("/register/verify-email"))).isFalse();
        assertThat(guards(dashboardReadOnlyInterceptor, post("/logout"))).isFalse();
        assertThat(guards(dashboardReadOnlyInterceptor, post("/Store/abc123def4/Checkout"))).isFalse();
    }

    @Test
    void readOnlyGateRunsAfterTheEmailCheck() {
        // given
        List<Object> interceptors = registeredInterceptors();

        // when
        int emailCheck = indexOf(interceptors, emailVerificationInterceptor);
        int readOnlyGate = indexOf(interceptors, dashboardReadOnlyInterceptor);

        // then
        assertThat(emailCheck).isNotNegative();
        assertThat(readOnlyGate).isGreaterThan(emailCheck);
    }

    @Test
    void publicGateGuardsTheShopApiAndClientPages() {
        // given / when / then
        assertThat(guards(publicStoreActivityInterceptor, post("/Store/abc123def4/Checkout"))).isTrue();
        assertThat(guards(publicStoreActivityInterceptor, get("/Store/abc123def4/Checkout/DeliveryOptions"))).isTrue();
        assertThat(guards(publicStoreActivityInterceptor, post("/Store/abc123def4/Basket"))).isTrue();
        assertThat(guards(publicStoreActivityInterceptor, get("/Store/abc123def4/Catalog/main"))).isTrue();
        assertThat(guards(publicStoreActivityInterceptor, get("/Store/abc123def4/Reporting/Google/Conversions/t"))).isTrue();
        assertThat(guards(publicStoreActivityInterceptor, post("/Store/abc123def4/Webhooks/Shipping/Furgonetka"))).isTrue();
        assertThat(guards(publicStoreActivityInterceptor, post("/Store/abc123def4/Webhooks/Receipts/Fiskator"))).isTrue();
        assertThat(guards(publicStoreActivityInterceptor, get("/store/abc123def4/client/order/o-1"))).isTrue();
        assertThat(guards(publicStoreActivityInterceptor, get("/store/abc123def4/client/rma/r-1"))).isTrue();
        assertThat(guards(publicStoreActivityInterceptor, get("/store/abc123def4/client/offer/f-1"))).isTrue();
        assertThat(guards(publicStoreActivityInterceptor, get("/store/abc123def4/individual/offer/f-1"))).isTrue();
    }

    @Test
    void publicGateLetsPaymentWebhooksAndSharedPathsThrough() {
        // given / when / then
        assertThat(guards(publicStoreActivityInterceptor, post("/Store/abc123def4/Webhooks/Payments/PayU"))).isFalse();
        assertThat(guards(publicStoreActivityInterceptor, get("/StoreLogo/abc123def4"))).isFalse();
        assertThat(guards(publicStoreActivityInterceptor, get("/Global/Inventory"))).isFalse();
        assertThat(guards(publicStoreActivityInterceptor, get("/dashboard/orders"))).isFalse();
    }

    @Test
    void publicGateRunsAfterTheApiKeyCheck() {
        // given
        List<Object> interceptors = registeredInterceptors();

        // when
        int apiKeyCheck = indexOf(interceptors, storeApiKeyAuthorizationInterceptor);
        int publicGate = indexOf(interceptors, publicStoreActivityInterceptor);

        // then
        assertThat(apiKeyCheck).isNotNegative();
        assertThat(publicGate).isGreaterThan(apiKeyCheck);
    }

    private boolean storeAccessGuards(HttpServletRequest request) {
        return guards(storeAccessInterceptor, request);
    }

    private boolean guards(HandlerInterceptor interceptor, HttpServletRequest request) {
        ServletRequestPathUtils.parseAndCache(request);
        return mappedInterceptorsOf(interceptor).stream().anyMatch(i -> i.matches(request));
    }

    private List<MappedInterceptor> mappedInterceptorsOf(HandlerInterceptor interceptor) {
        return registeredInterceptors().stream()
                .filter(MappedInterceptor.class::isInstance)
                .map(MappedInterceptor.class::cast)
                .filter(i -> i.getInterceptor() == interceptor)
                .toList();
    }

    private static int indexOf(List<Object> interceptors, HandlerInterceptor interceptor) {
        for (int i = 0; i < interceptors.size(); i++) {
            if (interceptors.get(i) instanceof MappedInterceptor mapped && mapped.getInterceptor() == interceptor) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private List<Object> registeredInterceptors() {
        return (List<Object>) ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
    }

    private HttpServletRequest get(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    private HttpServletRequest post(String path) {
        return new MockHttpServletRequest("POST", path);
    }
}

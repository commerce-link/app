package pl.commercelink.starter.security.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PathTenantResolverTest {

    private final PathTenantResolver resolver = new PathTenantResolver();

    @Test
    void readsTheStoreOfTheShopApiAndOfClientPages() {
        // when / then
        assertThat(resolver.resolveTenantId(get("/Store/abc123def4/Checkout"))).isEqualTo("abc123def4");
        assertThat(resolver.resolveTenantId(get("/store/abc123def4/client/order/o-1"))).isEqualTo("abc123def4");
    }

    @Test
    void decodesThePathLikeTheControllersAreMatchedAndGetTheirPathVariable() {
        // when / then
        assertThat(resolver.resolveTenantId(get("/Store/%61bc123def4/Basket"))).isEqualTo("abc123def4");
        assertThat(resolver.resolveTenantId(get("/%53tore/abc123def4/Basket"))).isEqualTo("abc123def4");
        assertThat(resolver.resolveTenantId(get("/%73tore/abc123def4/client/order/o-1"))).isEqualTo("abc123def4");
    }

    @Test
    void pathOutsideAStoreHasNoTenant() {
        // when / then
        assertThat(resolver.resolveTenantId(get("/Global/Inventory"))).isNull();
        assertThat(resolver.resolveTenantId(get("/dashboard/orders"))).isNull();
        assertThat(resolver.resolveTenantId(get("/Store"))).isNull();
    }

    private static MockHttpServletRequest get(String requestUri) {
        return new MockHttpServletRequest("GET", requestUri);
    }
}

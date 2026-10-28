package pl.commercelink.web.errorpage;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerPageErrorsTest {

    @ParameterizedTest
    @ValueSource(strings = {"/store/nova/client/offer/o1/nie-ma", "/store/nova/individual/offer/o1", "/store/nova/client",
            "/store/nova/client/rma/r1/submit", "/store/nova/%63lient/offer/o1"})
    void theErrorOfACustomerPageIsMatchedWithItsStore(String path) {
        // when / then
        assertThat(CustomerPageErrors.storeIdOf(path)).contains("nova");
    }

    @Test
    void theStoreIdIsDecoded() {
        // when / then
        assertThat(CustomerPageErrors.storeIdOf("/store/za%C5%BC%C3%B3%C5%82%C4%87/client/x")).contains("zażółć");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/Store/nova/Basket/b1", "/Store/nova/Webhooks/Payments/p1", "/Global/x", "/dashboard/nie-ma",
            "/store/nova/settings", "/store/nova", "/store//client/x", "/error", "/store/nova/client%zz/x"})
    void anyOtherPathIsNotACustomerPage(String path) {
        // when / then
        assertThat(CustomerPageErrors.storeIdOf(path)).isEmpty();
    }

    @Test
    void onlyTheErrorDispatchOfACustomerPageIsMatched() {
        // given
        MockHttpServletRequest error = new MockHttpServletRequest("GET", "/error");
        error.setDispatcherType(DispatcherType.ERROR);
        error.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/store/nova/client/offer/o1/nie-ma");
        MockHttpServletRequest apiError = new MockHttpServletRequest("POST", "/error");
        apiError.setDispatcherType(DispatcherType.ERROR);
        apiError.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/Store/nova/Webhooks/Payments/p1");
        MockHttpServletRequest direct = new MockHttpServletRequest("GET", "/error");
        MockHttpServletRequest page = new MockHttpServletRequest("GET", "/store/nova/client/offer/o1");

        // when / then
        assertThat(CustomerPageErrors.DISPATCH.matches(error)).isTrue();
        assertThat(CustomerPageErrors.DISPATCH.matches(apiError)).isFalse();
        assertThat(CustomerPageErrors.DISPATCH.matches(direct)).isFalse();
        assertThat(CustomerPageErrors.DISPATCH.matches(page)).isFalse();
    }
}

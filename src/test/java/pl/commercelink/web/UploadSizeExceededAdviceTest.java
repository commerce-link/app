package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadSizeExceededAdviceTest {

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private UploadSizeExceededAdvice advice;

    private MockHttpServletRequest post(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        request.addPreferredLocale(Locale.forLanguageTag("pl"));
        return request;
    }

    @Test
    void sendsABrandingSaveBackToItsPageWithAMessageAboutTheLogoSize() throws IOException {
        // given
        MockHttpServletRequest request = post("/dashboard/store/store-2/branding");
        when(messageSource.getMessage(eq("store.branding.logo.too.large"), any(), any(Locale.class))).thenReturn("Za duży");

        // when
        ModelAndView view = advice.handle(new MaxUploadSizeExceededException(10), request, new MockHttpServletResponse());

        // then
        assertThat(view.getView()).isInstanceOfSatisfying(RedirectView.class,
                redirect -> assertThat(redirect.getUrl()).isEqualTo("/dashboard/store/store-2/branding"));
        FlashMap flash = (FlashMap) request.getAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE);
        assertThat(flash.get("errorMessage")).isEqualTo("Za duży");
    }

    @Test
    void keepsThePlainPayloadTooLargeAnswerForOtherUploads() throws IOException {
        // given
        MockHttpServletRequest request = post("/dashboard/offer/new/csv");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        ModelAndView view = advice.handle(new MaxUploadSizeExceededException(10), request, response);

        // then
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(view.isEmpty()).isTrue();
    }
}

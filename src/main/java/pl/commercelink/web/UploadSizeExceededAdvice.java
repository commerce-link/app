package pl.commercelink.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * An upload over spring.servlet.multipart.max-file-size fails before any controller runs. A branding save goes back to
 * its page with a message; the form's own 1 MB check covers every smaller file with a message next to the field.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class UploadSizeExceededAdvice {

    private static final Pattern BRANDING_PAGE = Pattern.compile("/dashboard/store(/[^/]+)?/branding");

    private final MessageSource messageSource;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView handle(MaxUploadSizeExceededException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String uri = request.getRequestURI();
        if (!BRANDING_PAGE.matcher(uri).matches()) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value());
            return new ModelAndView();
        }
        RequestContextUtils.getOutputFlashMap(request).put("errorMessage",
                messageSource.getMessage("store.branding.logo.too.large", null, RequestContextUtils.getLocale(request)));
        return new ModelAndView(new RedirectView(uri, true));
    }
}

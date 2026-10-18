package pl.commercelink.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An upload over spring.servlet.multipart.max-file-size fails before any controller runs. The pages that take a file go
 * back to themselves with a message: a branding save (its form checks 1 MB itself, so this is only for bigger files) and
 * a new offer from CSV (the page shows errorMessage above its form). Other uploads keep the plain 413.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class UploadSizeExceededAdvice {

    private static final Pattern BRANDING_PAGE = Pattern.compile("/dashboard/store(/[^/]+)?/branding");
    private static final String OFFER_CSV_PAGE = "/dashboard/offer/new/csv";

    private final MessageSource messageSource;
    private final MultipartProperties multipartProperties;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView handle(MaxUploadSizeExceededException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String uri = request.getRequestURI();
        Locale locale = RequestContextUtils.getLocale(request);
        String message;
        if (BRANDING_PAGE.matcher(uri).matches()) {
            message = messageSource.getMessage("store.branding.logo.too.large", null, locale);
        } else if (OFFER_CSV_PAGE.equals(uri)) {
            message = messageSource.getMessage("offers.new.csv.file.too.large",
                    new Object[]{multipartProperties.getMaxFileSize().toMegabytes()}, locale);
        } else {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value());
            return new ModelAndView();
        }
        RequestContextUtils.getOutputFlashMap(request).put("errorMessage", message);
        return new ModelAndView(new RedirectView(uri, true));
    }
}

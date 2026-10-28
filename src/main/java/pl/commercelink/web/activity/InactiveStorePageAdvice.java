package pl.commercelink.web.activity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;
import pl.commercelink.web.publicpage.PublicPageFactory;

import java.util.Map;

@ControllerAdvice
class InactiveStorePageAdvice {

    // The customer came from the store's own link: the page keeps the store's brand and contact, since the seller is
    // the only one who can still answer about an order or a return.
    @ExceptionHandler(StoreInactiveException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ModelAndView storeInactive(StoreInactiveException e, HttpServletRequest request) {
        return new ModelAndView("store-inactive", Map.of("publicPage",
                PublicPageFactory.of(e.store(), request.getRequestURI(), RequestContextUtils.getLocale(request))));
    }
}

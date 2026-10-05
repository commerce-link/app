package pl.commercelink.web.activity;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
class InactiveStorePageAdvice {

    @ExceptionHandler(StoreInactiveException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    String storeInactive() {
        return "store-inactive";
    }
}

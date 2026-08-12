package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GtmAdvice {

    private final String containerId;

    public GtmAdvice(@Value("${app.gtm.container-id:}") String containerId) {
        this.containerId = containerId;
    }

    @ModelAttribute("gtmContainerId")
    public String gtmContainerId() {
        return containerId == null || containerId.isBlank() ? null : containerId;
    }
}

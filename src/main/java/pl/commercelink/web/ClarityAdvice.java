package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pl.commercelink.starter.security.CustomSecurityContext;

@ControllerAdvice
public class ClarityAdvice {

    private final String projectId;

    public ClarityAdvice(@Value("${app.clarity.project-id:}") String projectId) {
        this.projectId = projectId;
    }

    @ModelAttribute("clarityProjectId")
    public String clarityProjectId() {
        return projectId == null || projectId.isBlank() ? null : projectId;
    }

    @ModelAttribute("clarityStoreId")
    public String clarityStoreId() {
        return clarityProjectId() == null ? null : CustomSecurityContext.getStoreId();
    }
}

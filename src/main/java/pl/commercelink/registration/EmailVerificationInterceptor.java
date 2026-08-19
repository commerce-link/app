package pl.commercelink.registration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class EmailVerificationInterceptor implements HandlerInterceptor {

    private final EmailVerificationService emailVerificationService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (emailVerificationService.isVerified(request)) {
            return true;
        }
        response.sendRedirect(EmailVerificationController.VERIFY_EMAIL_PATH);
        return false;
    }
}

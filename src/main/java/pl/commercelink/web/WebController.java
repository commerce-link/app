package pl.commercelink.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.orders.OrderArchiveQuery;

import java.net.URI;
import java.time.LocalDate;

@Controller
public class WebController {

    @GetMapping("/dashboard")
    public String index() {
        if (CustomSecurityContext.hasRole("SUPER_ADMIN")) {
            return "redirect:/dashboard/stores";
        }
        return "redirect:/dashboard/orders";
    }

    /** The old "Klienci" page was the archive of closed orders; its bookmarks keep working (spec D6). */
    @GetMapping("/dashboard/clients")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> clients(@RequestParam(required = false) String orderId,
                                        @RequestParam(required = false) String email,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedAtStart,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedAtEnd) {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create(OrderArchiveQuery.legacyHref(orderId, email, orderedAtStart, orderedAtEnd))).build();
    }
}

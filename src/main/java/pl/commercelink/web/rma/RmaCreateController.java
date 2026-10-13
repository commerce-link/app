package pl.commercelink.web.rma;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.rma.RmaCreateService;
import pl.commercelink.orders.rma.RmaOrderSearch;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.dtos.RmaCreateForm;

import java.io.IOException;
import java.util.Locale;

@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
@RequestMapping("/dashboard/rma/new")
@RequiredArgsConstructor
public class RmaCreateController {

    private final RmaOrderSearch search;
    private final RmaNewPageFactory pages;
    private final RmaCreateService service;
    private final OrdersRepository orders;
    private final MessageSource messages;

    @InitBinder("rmaCreateForm")
    void binder(WebDataBinder binder) {
        // the store always comes from the session, never from the posted form
        binder.setAllowedFields("orderId", "email", "from", "items*");
    }

    @GetMapping
    public String show(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String from,
                       @RequestParam(required = false) String orderId,
                       @RequestParam(required = false) String serialNo,
                       @RequestParam(required = false, name = "byEmail") String byEmail,
                       @RequestParam(required = false) String serial,
                       Model model, Locale locale) {
        // addresses of the page before the redesign keep working
        String legacy = StringUtils.firstNonBlank(orderId, serialNo, byEmail);
        if (q == null && legacy != null) {
            // a template variable is encoded strictly: "+" and "@" in an e-mail become %2B and %40, so "+" is not read back as a space
            UriComponentsBuilder target = UriComponentsBuilder.fromPath("/dashboard/rma/new").queryParam("q", "{q}");
            if (from == null) {
                return "redirect:" + target.encode().buildAndExpand(legacy.trim()).toUriString();
            }
            target.queryParam("from", "{from}");
            return "redirect:" + target.encode().buildAndExpand(legacy.trim(), from).toUriString();
        }
        String storeId = storeId();
        model.addAttribute("page", pages.build(storeId, search.search(storeId, q), from, serial, locale));
        return "rma-new";
    }

    @PostMapping
    public String create(@ModelAttribute RmaCreateForm form, Model model, RedirectAttributes redirect, Locale locale) {
        String storeId = storeId();
        RmaCreateService.Outcome outcome = service.create(storeId, form);
        if (outcome.created()) {
            redirect.addFlashAttribute("successMessage", messages.getMessage("rma.create.success", null, locale));
            return "redirect:/dashboard/rma/" + outcome.rmaId();
        }
        Order order = RmaCreateService.readableOrderId(form.getOrderId()) ? orders.findById(storeId, form.getOrderId()) : null;
        if (order == null || !order.isEligibleForRMACreation()) {
            // the service may have seen the order eligible and rejected only the fields, then it changed before this read
            String errorKey = outcome.pageErrorKey() == null ? RmaCreateService.ORDER_GONE : outcome.pageErrorKey();
            RmaNewPage page = pages.build(storeId, search.search(storeId, form.getOrderId()), form.getFrom(), locale);
            model.addAttribute("page", page.withPageError(messages.getMessage(errorKey, null, locale)));
            return "rma-new";
        }
        model.addAttribute("page", pages.rebuild(storeId, order, form, outcome, locale));
        return "rma-new";
    }

    // an item index the page never prints (negative, or past the binder's 256 rows) only comes from a crafted request
    @ExceptionHandler(InvalidPropertyException.class)
    void refuseCraftedIndex(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.BAD_REQUEST.value());
    }

    private String storeId() {
        return CustomSecurityContext.getStoreId();
    }
}

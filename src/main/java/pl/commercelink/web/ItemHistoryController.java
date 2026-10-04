package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.orders.history.ItemHistoryService;
import pl.commercelink.web.itemhistory.ItemHistoryPageFactory;

import java.util.Locale;

@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
@RequestMapping("/dashboard/item/history")
@RequiredArgsConstructor
public class ItemHistoryController extends BaseController {

    private final ItemHistoryService historyService;
    private final ItemHistoryPageFactory pageFactory;

    @GetMapping
    public String viewHistory(@RequestParam(required = false) String serialNo, Model model, Locale locale) {
        String wanted = StringUtils.trimToNull(serialNo);
        model.addAttribute("page", wanted == null ? pageFactory.empty() : pageFactory.of(historyService.history(getStoreId(), wanted), locale));
        return "item-history";
    }

}

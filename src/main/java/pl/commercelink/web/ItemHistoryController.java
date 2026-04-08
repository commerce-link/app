package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.orders.ItemHistoryEvent;
import pl.commercelink.orders.ItemHistoryService;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
@RequestMapping("/dashboard/item/history")
public class ItemHistoryController extends BaseController {

    @Autowired
    private ItemHistoryService historyService;

    @GetMapping
    public String viewHistory(@RequestParam(required = false) String serialNo, Model model) {
        List<ItemHistoryEvent> history = new ArrayList<>();
        if(isNotBlank(serialNo)) {
            history = historyService.getHistoryBySerial(serialNo, getStoreId());
        }

        model.addAttribute("serialNo", serialNo);
        model.addAttribute("history", history);

        return "item-history";
    }

}

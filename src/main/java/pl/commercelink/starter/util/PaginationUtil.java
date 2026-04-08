package pl.commercelink.starter.util;

import org.springframework.ui.Model;

import java.util.Collections;
import java.util.List;

public class PaginationUtil {
    public static <T> List<T> paginate(List<T> items, int page, int pageSize, Model model) {
        int totalItems = items.size();
        int fromIndex = Math.max((page - 1) * pageSize, 0);
        int toIndex = Math.min(fromIndex + pageSize + 1, totalItems); // +1 to check for next page

        List<T> paginated = fromIndex < totalItems ? items.subList(fromIndex, toIndex) : Collections.emptyList();

        model.addAttribute("currentPage", page);
        model.addAttribute("hasNextPage", paginated.size() > pageSize);

        return paginated.subList(0, Math.min(paginated.size(), pageSize));
    }
}
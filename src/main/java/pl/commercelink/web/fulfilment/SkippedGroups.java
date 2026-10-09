package pl.commercelink.web.fulfilment;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The groups the operator skipped in the fulfilment queue, oldest first, as the queue's address carries them: orderIds in
 * skip order and skippedGroups with the size of each group ("5,1"). The sizes are what lets "Wróć" drop only the last
 * group. An address without valid sizes (an old link, a hand-edited one) counts every skipped order as one group.
 */
public record SkippedGroups(List<String> orderIds, List<Integer> sizes) {

    static final String PATH = "/dashboard/fulfilment/queue";

    public static SkippedGroups from(List<String> orderIds, String sizesParam) {
        List<String> ids = List.copyOf(new LinkedHashSet<>(orderIds));
        List<Integer> sizes = parse(sizesParam);
        boolean valid = !sizes.isEmpty() && sizes.stream().allMatch(size -> size > 0)
                && sizes.stream().mapToInt(Integer::intValue).sum() == ids.size();
        if (!valid) {
            sizes = ids.isEmpty() ? List.of() : List.of(ids.size());
        }
        return new SkippedGroups(ids, sizes);
    }

    private static List<Integer> parse(String sizesParam) {
        if (sizesParam == null || sizesParam.isBlank()) {
            return List.of();
        }
        List<Integer> sizes = new ArrayList<>();
        for (String part : sizesParam.split(",")) {
            try {
                sizes.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException e) {
                return List.of();
            }
        }
        return sizes;
    }

    public int count() {
        return sizes.size();
    }

    public int orderCount() {
        return orderIds.size();
    }

    /** Skipping a group adds the orders not skipped yet as one more group; a group of nothing new adds no group. */
    public SkippedGroups plus(List<String> groupOrderIds) {
        Set<String> ids = new LinkedHashSet<>(orderIds);
        int before = ids.size();
        ids.addAll(groupOrderIds);
        int added = ids.size() - before;
        List<Integer> nextSizes = new ArrayList<>(sizes);
        if (added > 0) {
            nextSizes.add(added);
        }
        return new SkippedGroups(List.copyOf(ids), List.copyOf(nextSizes));
    }

    public String sizesParam() {
        return sizes.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** The queue as it was before the last skip, or null when nothing was skipped. */
    public String backHref() {
        if (sizes.isEmpty()) {
            return null;
        }
        int keep = orderIds.size() - sizes.get(sizes.size() - 1);
        SkippedGroups previous = new SkippedGroups(orderIds.subList(0, keep), sizes.subList(0, sizes.size() - 1));
        return previous.queueHref();
    }

    /** The queue with exactly these groups skipped; the bare path when none are. The selection page returns here. */
    public String queueHref() {
        if (orderIds.isEmpty()) {
            return PATH;
        }
        // the ids go in as URI variables, encoded strictly on expansion: a literal "{x}" in an id would otherwise reach
        // the "redirect:" view as a template variable and fail it, and "&" or "#" could break the query
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(PATH);
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < orderIds.size(); i++) {
            builder.queryParam("orderIds", "{id" + i + "}");
            values.put("id" + i, orderIds.get(i));
        }
        return builder.queryParam("skippedGroups", sizesParam()).encode().buildAndExpand(values).toUriString();
    }
}

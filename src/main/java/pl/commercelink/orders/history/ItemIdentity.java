package pl.commercelink.orders.history;

import pl.commercelink.warehouse.api.WarehouseItemView;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/** The product behind the number, from the newest record that has one; productCount > 1 means different products. */
public record ItemIdentity(String name, String ean, String mfn, int productCount) {

    private record Product(String name, String ean, String mfn) {
    }

    public static ItemIdentity of(List<OrderLine> orders, List<RmaLine> rmas, List<WarehouseItemView> warehouse) {
        Stream<Product> fromOrders = orders.stream()
                .sorted(Comparator.comparing(OrderLine::placedAt, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())))
                .map(l -> new Product(l.item().getName(), l.item().getEan(), l.item().getManufacturerCode()));
        Stream<Product> fromRmas = rmas.stream()
                .sorted(Comparator.comparing((RmaLine l) -> l.rma().getCreatedAt(), Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())))
                .map(l -> new Product(l.item().getName(), l.item().getEan(), l.item().getMfn()));
        Stream<Product> fromWarehouse = warehouse.stream().map(w -> new Product(w.getName(), w.getEan(), w.getMfn()));
        List<Product> products = Stream.of(fromOrders, fromRmas, fromWarehouse).flatMap(s -> s).toList();

        Product newest = products.stream().filter(p -> isNotBlank(p.name())).findFirst()
                .orElse(products.isEmpty() ? new Product(null, null, null) : products.get(0));
        long eans = products.stream().map(Product::ean).filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().count();
        long mfns = products.stream().map(Product::mfn).filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().count();
        int count = (int) Math.max(Math.max(eans, mfns), products.isEmpty() ? 0 : 1);
        return new ItemIdentity(newest.name(), newest.ean(), newest.mfn(), count);
    }
}

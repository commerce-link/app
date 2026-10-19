package pl.commercelink.orders.history;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** The product behind the number, from the newest records that have each field; productCount > 1 means different products. */
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

        String name = products.stream().map(Product::name).filter(StringUtils::isNotBlank).findFirst().orElse(null);
        String ean = products.stream().map(Product::ean).filter(StringUtils::isNotBlank).findFirst().orElse(null);
        String mfn = products.stream().map(Product::mfn).filter(StringUtils::isNotBlank).findFirst().orElse(null);
        long eanCount = countDistinctNonBlank(products.stream().map(Product::ean));
        long mfnCount = countDistinctNonBlank(products.stream().map(Product::mfn));
        int count = (int) Math.max(Math.max(eanCount, mfnCount), products.isEmpty() ? 0 : 1);
        return new ItemIdentity(name, ean, mfn, count);
    }

    private static long countDistinctNonBlank(Stream<String> values) {
        return values.filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().count();
    }
}

package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import pl.commercelink.orders.SplitGroupComponent;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SplitGroupForm {

    private String itemId;
    private List<Row> rows = new ArrayList<>();

    public List<SplitGroupComponent> toComponents() {
        return rows.stream()
                .map(r -> new SplitGroupComponent(r.getSku(), r.getName(), r.getQty(), r.getPrice()))
                .toList();
    }

    @Getter
    @Setter
    public static class Row {
        private String sku;
        private String name;
        private int qty;
        private double price;
    }

}

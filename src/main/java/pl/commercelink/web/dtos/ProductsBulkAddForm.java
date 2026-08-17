package pl.commercelink.web.dtos;

import pl.commercelink.products.Product;

import java.util.ArrayList;
import java.util.List;

public class ProductsBulkAddForm {
    private List<Product> products = new ArrayList<>();

    public ProductsBulkAddForm() {}

    public ProductsBulkAddForm(List<Product> products) {
        this.products = products;
    }

    public List<Product> getProducts() {
        return products;
    }

    public void setProducts(List<Product> products) {
        this.products = products;
    }
}

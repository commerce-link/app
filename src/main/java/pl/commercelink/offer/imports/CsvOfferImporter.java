package pl.commercelink.offer.imports;

import org.springframework.stereotype.Component;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.web.dtos.OfferCreationDto;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static pl.commercelink.starter.util.ConversionUtil.*;

@Component
public class CsvOfferImporter implements OfferImporter {

    public List<BasketItem> importOffer(OfferCreationDto dto) throws IOException {
        InputStream is = dto.getCsvFile().getInputStream();
        CSVLoader csvLoader = new CSVLoader(new InputStreamReader(is));
        List<BasketItem> basketItems = new ArrayList<>();
        AtomicInteger rowNumber = new AtomicInteger(1);
        csvLoader.readRows(CSVLoader.DEFAULT_SEPARATOR, row -> {
            basketItems.add(mapToBasketItem(row, rowNumber.incrementAndGet()));
        });
        return basketItems;
    }

    private BasketItem mapToBasketItem(String[] row, int rowNumber) {
        return new BasketItem(
                "", // pimId - empty for CSV imports
                asString(row[1]), // name
                asStringOrDefault(row, 5, ""), // manufacturer code
                validCategory(asString(row[0]), rowNumber),
                asLong(row[3]), // price
                asDouble(row[4]),
                asInt(row[2]), // quantity
                null,
                1,
                false
        );
    }

    private String validCategory(String category, int rowNumber) {
        try {
            ProductCategory.valueOf(category);
            return category;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown category '" + category + "' in row " + rowNumber);
        }
    }

    @Override
    public String getType() {
        return "CSV";
    }
}
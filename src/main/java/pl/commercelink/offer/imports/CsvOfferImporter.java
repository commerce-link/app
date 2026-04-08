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

import static pl.commercelink.starter.util.ConversionUtil.*;

@Component
public class CsvOfferImporter implements OfferImporter {

    public List<BasketItem> importOffer(OfferCreationDto dto) throws IOException {
        InputStream is = dto.getCsvFile().getInputStream();
        CSVLoader csvLoader = new CSVLoader(new InputStreamReader(is));
        List<BasketItem> basketItems = new ArrayList<>();
        csvLoader.readRows(CSVLoader.DEFAULT_SEPARATOR, row -> {
            basketItems.add(mapToBasketItem(row));
        });
        return basketItems;
    }

    private BasketItem mapToBasketItem(String[] row) {
        return new BasketItem(
                "", // pimId - empty for CSV imports
                asString(row[1]), // name
                asStringOrDefault(row, 5, ""), // manufacturer code
                ProductCategory.valueOf(asString(row[0])),
                asLong(row[3]), // price
                asDouble(row[4]),
                asInt(row[2]), // quantity
                null,
                1,
                false
        );
    }

    @Override
    public String getType() {
        return "CSV";
    }
}
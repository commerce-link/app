package pl.commercelink.offer.imports;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.web.dtos.OfferCreationDto;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvOfferImporterTest {

    private final CsvOfferImporter importer = new CsvOfferImporter();

    @Test
    void importsRowWithKnownCategory() throws IOException {
        // given
        OfferCreationDto dto = dtoWithCsv("Category;Name;Qty;Price;Cost;Mfn\n" +
                "CPU;Ryzen 7;2;1500;1200.5;100-100000065BOX");

        // when
        List<BasketItem> items = importer.importOffer(dto);

        // then
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getCategory()).isEqualTo("CPU");
        assertThat(items.get(0).getName()).isEqualTo("Ryzen 7");
    }

    @Test
    void importsRowWithCategoryFromProductCatalog() throws IOException {
        // given
        OfferCreationDto dto = dtoWithCsv("Category;Name;Qty;Price;Cost;Mfn\n" +
                "Obudowa;Fractal Design North;1;600;500.0;FD-C-NOR1C-01");

        // when
        List<BasketItem> items = importer.importOffer(dto);

        // then
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getCategory()).isEqualTo("Obudowa");
    }

    private OfferCreationDto dtoWithCsv(String csv) {
        OfferCreationDto dto = new OfferCreationDto();
        dto.setCsvFile(new MockMultipartFile("csvFile", "offer.csv", "text/csv", csv.getBytes()));
        return dto;
    }
}

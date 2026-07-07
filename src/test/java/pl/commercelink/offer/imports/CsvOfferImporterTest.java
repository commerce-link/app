package pl.commercelink.offer.imports;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.web.dtos.OfferCreationDto;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void rejectsUnknownCategoryPointingAtTheRow() {
        // given
        OfferCreationDto dto = dtoWithCsv("Category;Name;Qty;Price;Cost;Mfn\n" +
                "CPU;Ryzen 7;2;1500;1200.5;mfn1\n" +
                "Smartwatches;Watch 5;1;900;700.0;mfn2");

        // when / then
        assertThatThrownBy(() -> importer.importOffer(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Smartwatches")
                .hasMessageContaining("row 3");
    }

    private OfferCreationDto dtoWithCsv(String csv) {
        OfferCreationDto dto = new OfferCreationDto();
        dto.setCsvFile(new MockMultipartFile("csvFile", "offer.csv", "text/csv", csv.getBytes()));
        return dto;
    }
}

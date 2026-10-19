package pl.commercelink.offer.imports;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.web.dtos.OfferCreationDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void importsRowWithServiceColumnSetToTrueAsService() throws IOException {
        // given
        OfferCreationDto dto = dtoWithCsv("Category;Name;Qty;Price;Cost;Mfn;Service\n" +
                "Usługi dodatkowe;Montaż komputera;1;250;0;;true");

        // when
        List<BasketItem> items = importer.importOffer(dto);

        // then
        assertThat(items).hasSize(1);
        assertThat(items.get(0).isService()).isTrue();
    }

    @Test
    void importsOldFormatRowWithoutServiceColumnAsNotAService() throws IOException {
        // given
        OfferCreationDto dto = dtoWithCsv("Category;Name;Qty;Price;Cost;Mfn\n" +
                "CPU;Ryzen 7;2;1500;1200.5;100-100000065BOX");

        // when
        List<BasketItem> items = importer.importOffer(dto);

        // then
        assertThat(items).hasSize(1);
        assertThat(items.get(0).isService()).isFalse();
    }

    @Test
    void importsTheExampleFileOfferedOnTheNewOfferPage() throws IOException {
        // given
        OfferCreationDto dto = new OfferCreationDto();
        dto.setCsvFile(new MockMultipartFile("csvFile", "offer-import-example.csv", "text/csv",
                Files.readAllBytes(Path.of("src/main/resources/static/files/offer-import-example.csv"))));

        // when
        List<BasketItem> items = importer.importOffer(dto);

        // then
        assertThat(items).hasSize(2).allMatch(BasketItem::isComplete);
        assertThat(items.get(0).getCategory()).isEqualTo("Karty graficzne");
        assertThat(items.get(0).getQty()).isEqualTo(2);
        assertThat(items.get(0).getUnitPrice()).isEqualTo(2899);
        assertThat(items.get(0).getUnitCost()).isEqualTo(2410.50);
        assertThat(items.get(0).getMfn()).isEqualTo("GV-N5070EAGLE");
        assertThat(items.get(1).getName()).isEqualTo("Montaż zestawu");
        assertThat(items.get(1).isService()).isTrue();
        assertThat(items.get(1).getMfn()).isEqualTo("MONTAZ-PC");
    }

    @Test
    void keepsAndDropsRowsTheWayTheNewOfferPageDescribesTheFormat() throws IOException {
        // given: the page says an empty category is fine and a row without a name, a manufacturer code or quantity is left out
        String csv = "kategoria;nazwa;ilosc;cena;koszt;kod;usluga\n"
                + ";Bez kategorii;1;100;80;KOD-1;false\n"
                + "Dyski;Bez kodu;1;100;80;;false\n"
                + "Dyski;Zero sztuk;0;100;80;KOD-2;false\n"
                + "Dyski;;1;100;80;KOD-3;false\n"
                + "Dyski;Pełny wiersz;2;100;80.50;KOD-4;false\n";

        // when
        List<BasketItem> complete = importer.importOffer(dtoWithCsv(csv)).stream().filter(BasketItem::isComplete).toList();

        // then
        assertThat(complete).extracting(BasketItem::getName).containsExactly("Bez kategorii", "Pełny wiersz");
    }

    private OfferCreationDto dtoWithCsv(String csv) {
        OfferCreationDto dto = new OfferCreationDto();
        dto.setCsvFile(new MockMultipartFile("csvFile", "offer.csv", "text/csv", csv.getBytes()));
        return dto;
    }
}

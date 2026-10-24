package pl.commercelink.web.clientreturn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.rma.RMAReturnOption;
import pl.commercelink.orders.rma.RMAShippingService;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Parcel;
import pl.commercelink.stores.Store;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ReturnPackageOptionsTest {

    @Mock
    private Store store;

    private PackageTemplate template(String id, String name, boolean isDefault, Parcel... parcels) {
        PackageTemplate template = new PackageTemplate(name, List.of(parcels));
        template.setId(id);
        template.setDefault(isDefault);
        return template;
    }

    private void storeHas(PackageTemplate... templates) {
        lenient().when(store.getPackageTemplates()).thenReturn(List.of(templates));
    }

    @Test
    void offersTheSameTemplatesInTheSameOrderAsTheShippingService() {
        // given: return templates mixed with outgoing ones, near misses of the prefix included
        storeHas(template("out-1", "Karton wysyłkowy", false),
                template("r-1", "RMA - komputer", false),
                template("near-1", "RMA-bez spacji", false),
                template("r-2", "RMA - karta graficzna", true),
                template("near-2", "rma - mała litera", false),
                template("out-2", "Paleta RMA - ", false),
                template("r-3", "RMA - ", false));

        // when
        List<String> page = ReturnPackageOptions.of(store).stream().map(ReturnPackageOption::id).toList();
        List<String> service = new RMAShippingService().getAvailableReturnOptions(store).stream()
                .map(RMAReturnOption::getId).toList();

        // then
        assertThat(page).containsExactly("r-1", "r-2", "r-3").isEqualTo(service);
    }

    @Test
    void stripsThePrefixAndCapitalisesTheTitle() {
        // given
        storeHas(template("a", "RMA - karta graficzna", false),
                template("b", "RMA - Komputer - 60 x 35 x 55", false));

        // when
        List<ReturnPackageOption> options = ReturnPackageOptions.of(store);

        // then
        assertThat(options).extracting(ReturnPackageOption::title)
                .containsExactly("Karta graficzna", "Komputer - 60 x 35 x 55");
    }

    @Test
    void describesEveryParcel() {
        // given
        storeHas(template("two", "RMA - komputer 80x40x60 + karta graficzna", true,
                        new Parcel(80, 40, 60, 12, 0, null), new Parcel(60, 40, 40, 5, 0, null)),
                template("one", "RMA - mała", false, new Parcel(25, 15, 10, 1, 0, null)),
                template("none", "RMA - pusta", false));

        // when
        List<ReturnPackageOption> options = ReturnPackageOptions.of(store);

        // then
        assertThat(options.get(0).parcelCount()).isEqualTo(2);
        assertThat(options.get(0).parcels()).isEqualTo("80 × 40 × 60 cm · 12 kg + 60 × 40 × 40 cm · 5 kg");
        assertThat(options.get(0).isDefault()).isTrue();
        assertThat(options.get(1).parcels()).isEqualTo("25 × 15 × 10 cm · 1 kg");
        assertThat(options.get(2).parcelCount()).isZero();
        assertThat(options.get(2).parcels()).isNull();
    }

    @Test
    void skipsTemplatesWithoutThePrefix() {
        // given
        storeHas(template("a", "Standard", true), template("b", "RMA - karton", false));

        // when
        List<ReturnPackageOption> options = ReturnPackageOptions.of(store);

        // then
        assertThat(options).extracting(ReturnPackageOption::id).containsExactly("b");
    }

    @Test
    void findDescribesAnyTemplateOfTheStore() {
        // given
        storeHas(template("a", "Standard", true, new Parcel(10, 10, 10, 2, 0, null)));

        // when
        var found = ReturnPackageOptions.find(store, "a");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("Standard");
        assertThat(ReturnPackageOptions.find(store, "zzz")).isEmpty();
    }

    @Test
    void selectedPrefersThePostedThenTheDefaultThenTheFirst() {
        // given
        var first = new ReturnPackageOption("1", "A", 1, null, false);
        var second = new ReturnPackageOption("2", "B", 1, null, true);

        // when // then
        assertThat(ReturnPackageOptions.selected(List.of(first, second), "1")).isEqualTo("1");
        assertThat(ReturnPackageOptions.selected(List.of(first, second), "unknown")).isEqualTo("2");
        assertThat(ReturnPackageOptions.selected(List.of(first, second), null)).isEqualTo("2");
        assertThat(ReturnPackageOptions.selected(List.of(first), null)).isEqualTo("1");
    }

    @Test
    void selectedOfNoOptionsIsNull() {
        // given // when // then
        assertThat(ReturnPackageOptions.selected(List.of(), "x")).isNull();
    }
}

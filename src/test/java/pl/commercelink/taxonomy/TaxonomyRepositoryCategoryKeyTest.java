package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.starter.storage.FileStorage;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxonomyRepositoryCategoryKeyTest {

    @Mock
    private FileStorage fileStorage;
    @Mock
    private BrandMapper brandMapper;
    @InjectMocks
    private TaxonomyRepository repository;

    @Test
    void reloadKeepsNonEnumCategoryKeyFromCsv() throws Exception {
        // given
        setField(repository, "bucketName", "datalake");
        when(brandMapper.unifyBrand(anyString())).thenAnswer(inv -> inv.getArgument(0));
        String csv = "ean;mfn;brand;name;category;data_accuracy_score;net_weight_g;gross_weight_g;category_key\n"
                + "1234567890123;MFN-1;Brand;Name;Other;5;;;Cables356k";
        when(fileStorage.findNewest("datalake", "taxonomy/"))
                .thenReturn(Pair.of("taxonomy/2026-06-25.csv",
                        new InputStreamReader(new ByteArrayInputStream(csv.getBytes()))));

        // when
        Pair<String, List<Taxonomy>> result = repository.loadNewest();

        // then
        assertThat(result.getRight()).hasSize(1);
        assertThat(result.getRight().get(0).categoryKey()).isEqualTo("Cables356k");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

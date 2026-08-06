package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.starter.storage.FileStorage;

import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxonomyRepositoryTest {

    @Mock
    private FileStorage fileStorage;

    @Mock
    private BrandMapper brandMapper;

    @InjectMocks
    private TaxonomyRepository taxonomyRepository;

    @Test
    void loadNewestDoesNotSwallowSubsequentRowsAfterUnbalancedQuoteInProductName() {
        // given
        String csv = "ean;mfn;brand;name;category;category_id;data_accuracy_score;net_weight_g;gross_weight_g\r\n"
                + "8024221014761;066;Manfrotto;\"Manfrotto Zlaczka 3/8\\ na 1/4\\\"\" (66)\"\"\";Piloty do aparatow;3242;50;;\r\n"
                + "4044951018031;4044951018031;SHARKOON;Kabel Sharkoon HDMI - HDMI 2m czarny;Kable sieciowe;883;50;;\r\n";
        givenTaxonomyFile(csv);

        // when
        List<Taxonomy> taxonomies = taxonomyRepository.loadNewest().getRight();

        // then
        assertThat(taxonomies).hasSize(2);
        assertThat(taxonomies).anyMatch(t -> "4044951018031".equals(t.mfn())
                && "Kable sieciowe".equals(t.category()));
    }

    private void givenTaxonomyFile(String csv) {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
        when(fileStorage.findNewest(any(), anyString())).thenReturn(Pair.of("taxonomy-merged-full.csv", reader));
        when(brandMapper.unifyBrand(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }
}

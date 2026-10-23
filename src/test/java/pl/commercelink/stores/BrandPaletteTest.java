package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BrandPaletteTest {

    @Test
    void noColourFallsBackToSystemAccent() {
        // when
        BrandPalette palette = BrandPalette.of(null);

        // then
        assertThat(palette.fill()).isEqualTo("#1b4db1");
        assertThat(palette.on()).isEqualTo("#ffffff");
        assertThat(palette.ink()).isEqualTo("#1b4db1");
    }

    @Test
    void darkBrandKeepsWhiteText() {
        // when
        BrandPalette palette = BrandPalette.of("#0f766e");

        // then
        assertThat(palette.on()).isEqualTo("#ffffff");
        assertThat(palette.ink()).isEqualTo("#0f766e");
        assertThat(BrandPalette.contrast(palette.fill(), palette.on())).isCloseTo(5.47, within(0.01));
    }

    @Test
    void lightBrandGetsInkTextAndDarkenedAccent() {
        // when
        BrandPalette palette = BrandPalette.of("#ffd400");

        // then
        assertThat(palette.on()).isEqualTo("#0e1721");
        assertThat(BrandPalette.contrast(palette.fill(), palette.on())).isCloseTo(12.61, within(0.01));
        assertThat(palette.ink()).isEqualTo("#806a00");
        assertThat(BrandPalette.contrast(palette.ink(), palette.soft())).isCloseTo(5.07, within(0.01));
    }

    @Test
    void midToneBrandGetsBlackText() {
        // when
        BrandPalette palette = BrandPalette.of("#2277ee");

        // then
        assertThat(palette.on()).isEqualTo("#000000");
    }

    @Test
    void whiteBrandStaysReadable() {
        // when
        BrandPalette palette = BrandPalette.of("#ffffff");

        // then
        assertThat(palette.on()).isIn("#0e1721", "#000000");
        assertThat(BrandPalette.contrast(palette.ink(), "#ffffff")).isGreaterThanOrEqualTo(4.5);
    }

    @Test
    void paleBrandButtonGetsADarkEdgeOnTheWhiteCard() {
        // when
        BrandPalette white = BrandPalette.of("#ffffff");
        BrandPalette yellow = BrandPalette.of("#ffd400");

        // then
        assertThat(white.edge()).isEqualTo(white.ink());
        assertThat(white.edgeHover()).isEqualTo(white.ink());
        assertThat(yellow.edge()).isEqualTo("#806a00");
    }

    @Test
    void hoverLightenedBelowTheEdgeContrastKeepsTheDarkEdge() {
        // given — #2277ee takes black text, so its hover is lightened, to 2.89:1 on white
        BrandPalette palette = BrandPalette.of("#2277ee");

        // when
        String edgeHover = palette.edgeHover();

        // then
        assertThat(palette.edge()).isEqualTo("#2277ee");
        assertThat(edgeHover).isEqualTo(palette.ink());
    }

    @Test
    void brandThatStandsOutOnWhiteIsItsOwnEdge() {
        // when
        BrandPalette palette = BrandPalette.of("#0f766e");

        // then
        assertThat(palette.edge()).isEqualTo("#0f766e");
        assertThat(palette.edgeHover()).isEqualTo(palette.hover());
    }

    @Test
    void everyColourHasReadableTextAndAccent() {
        for (int r = 0; r <= 255; r += 17) {
            for (int g = 0; g <= 255; g += 17) {
                for (int b = 0; b <= 255; b += 17) {
                    // given
                    String hex = String.format("#%02x%02x%02x", r, g, b);

                    // when
                    BrandPalette palette = BrandPalette.of(hex);

                    // then
                    assertThat(BrandPalette.contrast(palette.fill(), palette.on())).as("on for %s", hex).isGreaterThanOrEqualTo(4.5);
                    assertThat(BrandPalette.contrast(palette.ink(), "#ffffff")).as("ink for %s", hex).isGreaterThanOrEqualTo(4.5);
                    assertThat(BrandPalette.contrast(palette.ink(), palette.soft())).as("ink on soft for %s", hex).isGreaterThanOrEqualTo(4.5);
                    assertThat(BrandPalette.contrast(palette.hover(), palette.on())).as("hover for %s", hex).isGreaterThanOrEqualTo(4.5);
                    assertThat(BrandPalette.contrast(palette.edge(), "#ffffff")).as("button edge for %s", hex).isGreaterThanOrEqualTo(3.0);
                    assertThat(BrandPalette.contrast(palette.edgeHover(), "#ffffff")).as("hovered button edge for %s", hex).isGreaterThanOrEqualTo(3.0);
                }
            }
        }
    }

    @Test
    void styleListsOnlyHexValues() {
        // when
        String style = BrandPalette.of("#0f766e").style();

        // then
        assertThat(style).matches("(--cl-brand[a-z-]*:#[0-9a-f]{6};){7}--cl-brand-edge-hover:#[0-9a-f]{6}");
    }
}

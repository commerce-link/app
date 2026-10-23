package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The public page frame and the client offer styles in commercelink.css. */
class PublicPageStylesContractTest {

    private static String publicCss() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));
        return css.substring(css.indexOf("/* ===== Public page"));
    }

    @Test
    void brandLinkColourReachesOnlyContentAndFooterLinks() throws Exception {
        // given — the skip link sits on the brand ink background and the language switch has its own colours
        String css = publicCss();

        // then
        assertThat(css).contains("body.cl-public :is(.cl-public-main, .cl-public-foot) a:not(.cl-button) { color: var(--cl-brand-ink); }")
                .doesNotContain("body.cl-public a:not(.cl-button)")
                .doesNotContain("body.cl-public a {");
    }

    @Test
    void languageSwitchKeepsItsColoursWithoutImportant() throws Exception {
        // given
        String css = publicCss();
        String lang = css.substring(css.indexOf(".cl-public-lang a, .cl-public-lang span {"), css.indexOf(".cl-public-main {"));

        // then
        assertThat(lang).contains("color: var(--cl-ink-2);")
                .contains(".cl-public-lang [aria-current] { color: var(--cl-ink);")
                .doesNotContain("!important");
    }

    @Test
    void phoneHeaderBarIsFiftySixPixelsHigh() throws Exception {
        // given — 44 px language targets plus 6 + 6 px padding
        String css = publicCss();

        // then
        assertThat(css).contains("  .cl-public-bar { min-height: 56px; padding: 6px 16px; gap: 8px; }")
                .contains("  .cl-public-lang a, .cl-public-lang span { min-width: 44px; min-height: 44px; }");
    }

    @Test
    void consecutiveHelpLinesInTheOfferCardKeepAGap() throws Exception {
        // given
        String css = publicCss();

        // then
        assertThat(css).contains(".cl-client-offer .cl-card-inset > .cl-help + .cl-help { margin-top: 8px; }");
    }

    @Test
    void paymentMethodChoiceUsesTheSurfaceToken() throws Exception {
        // given
        String css = publicCss();
        String offer = css.substring(css.indexOf("/* ===== Client offer"));

        // then
        assertThat(offer).doesNotContain("#fff")
                .contains("cursor: pointer; background: var(--cl-surface); }");
    }

    @Test
    void primaryButtonEdgeComesFromThePalette() throws Exception {
        // given — a pale brand needs a darker edge than its own fill to show the button on the white card
        String css = publicCss();

        // then
        assertThat(css).contains("body.cl-public .cl-button.is-primary { background: var(--cl-brand); border-color: var(--cl-brand-edge);")
                .contains("body.cl-public .cl-button.is-primary:hover { background: var(--cl-brand-hover); border-color: var(--cl-brand-edge-hover);");
    }

    @Test
    void messageCardHasInnerSpacing() throws Exception {
        // given
        String css = publicCss();

        // then
        assertThat(css).contains(".cl-public-main .cl-card.cl-public-message { padding: 28px 24px; }");
    }

    @Test
    void variantButtonIsHiddenFromTheFirstPaintWhenScriptsRun() throws Exception {
        // given — client-offer.js is deferred behind FontAwesome, so hiding the button from the script alone made it flash
        String css = publicCss();

        // then
        assertThat(css).contains("@media (scripting: enabled) {\n  .cl-client-offer .cl-variant-apply:not([data-cl-revealed]) { display: none; }");
    }

    @Test
    void redirectTargetsKeepAGapToTheTopEdge() throws Exception {
        // given
        String css = publicCss();

        // then
        assertThat(css).contains(".cl-client-offer .cl-list-item.is-group[id], .cl-client-offer #client-offer-pay-title { scroll-margin-top: 16px; }");
    }

    @Test
    void publicRulesAreScopedByClassesNoDashboardTemplateUses() throws Exception {
        // given — the public block once scoped the offer by .cl-offer-page, which the dashboard offer details took too, so the
        // customer page styles reached the dashboard; every rule must open with a class that only public templates carry
        String css = publicCss().replaceAll("(?s)/\\*.*?\\*/", "");
        Set<String> scopes = new TreeSet<>();
        Matcher rule = Pattern.compile("([^{}]+)\\{").matcher(css);
        while (rule.find()) {
            String selectors = rule.group(1).trim();
            if (selectors.startsWith("@") || selectors.matches("from|to|[\\d.]+%")) {
                continue;
            }
            for (String selector : splitTopLevel(selectors)) {
                Matcher first = Pattern.compile("^[^\\s>+~]+").matcher(selector);
                assertThat(first.find()).as(selector).isTrue();
                Matcher scope = Pattern.compile("\\.(cl-[a-z0-9-]+)").matcher(first.group());
                assertThat(scope.find()).as("rule without a class scope: %s", selector).isTrue();
                scopes.add(scope.group(1));
            }
        }

        // when
        Map<String, List<String>> usedOutside = new TreeMap<>();
        Path templates = Path.of("src/main/resources/templates");
        try (Stream<Path> files = Files.walk(templates)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".html")).toList()) {
                String html = Files.readString(file);
                if (isPublicTemplate(templates.relativize(file).toString(), html)) {
                    continue;
                }
                for (String scope : scopes) {
                    if (Pattern.compile("[\"'\\s]" + Pattern.quote(scope) + "[\"'\\s]").matcher(html).find()) {
                        usedOutside.computeIfAbsent(scope, s -> new ArrayList<>()).add(templates.relativize(file).toString());
                    }
                }
            }
        }

        // then
        assertThat(usedOutside).as("public scope classes used by other templates").isEmpty();
        assertThat(scopes).contains("cl-public", "cl-client-offer");
    }

    private static boolean isPublicTemplate(String path, String html) {
        return path.equals("public-layout.html") || path.startsWith("client-offer/")
                || html.contains("layout:decorate=\"~{public-layout}\"");
    }

    private static List<String> splitTopLevel(String selectors) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : selectors.toCharArray()) {
            depth += c == '(' ? 1 : c == ')' ? -1 : 0;
            if (c == ',' && depth == 0) {
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString().trim());
        return parts;
    }

    @Test
    void variantUpdateSpinnerShowsOnlyWhenTheAnswerIsSlow() throws Exception {
        // given — a fast update must not flash the spinner
        String css = publicCss();

        // then
        assertThat(css).contains(".cl-client-offer .cl-offer-updating { display: none; }")
                .contains(".cl-client-offer #client-offer-items.is-slow .cl-offer-updating { display: inline-block; }");
    }
}

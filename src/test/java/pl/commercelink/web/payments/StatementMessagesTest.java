package pl.commercelink.web.payments;

import org.junit.jupiter.api.Test;
import pl.commercelink.payments.statements.MatchKind;
import pl.commercelink.payments.statements.MatchRule;
import pl.commercelink.payments.statements.MatchWarning;
import pl.commercelink.payments.statements.ParsedStatement;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class StatementMessagesTest {

    private static final List<String> PREFIXES = List.of("payments.statement.", "store.payments.statement.", "statement.");

    @Test
    void polishAndEnglishDefineTheSameStatementKeys() throws Exception {
        // when / then
        assertThat(keys("messages_pl.properties")).isEqualTo(keys("messages_en.properties"));
    }

    @Test
    void everyRuleWarningAndGroupHasALabel() throws Exception {
        // given
        List<String> wanted = new ArrayList<>();
        Arrays.stream(MatchRule.values()).forEach(rule -> wanted.add("payments.statement.rule." + rule.name()));
        Arrays.stream(MatchWarning.values()).forEach(warning -> wanted.add("payments.statement.warning." + warning.name()));
        Arrays.stream(MatchKind.values()).forEach(kind -> wanted.add("payments.statement.group." + kind.name()));
        Arrays.stream(ParsedStatement.Problem.values()).forEach(problem -> wanted.add("store.payments.statement.preview.problem." + problem.name()));

        // when / then
        assertThat(keys("messages_pl.properties")).containsAll(wanted);
    }

    @Test
    void newPolishTextsAreEscapedLikeTheRestOfTheFile() throws Exception {
        // given
        List<String> lines = Files.readAllLines(Path.of("src/main/resources/messages_pl.properties"), StandardCharsets.UTF_8);

        // when
        List<String> raw = lines.stream()
                .filter(line -> PREFIXES.stream().anyMatch(line::startsWith))
                .filter(line -> line.chars().anyMatch(c -> c > 127))
                .toList();

        // then
        assertThat(raw).isEmpty();
    }

    private static Set<String> keys(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties.stringPropertyNames().stream()
                .filter(key -> PREFIXES.stream().anyMatch(key::startsWith))
                .collect(Collectors.toSet());
    }
}

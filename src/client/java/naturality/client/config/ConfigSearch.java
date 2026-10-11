package naturality.client.config;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Text terms narrow the results; multiple impact tags accept any of the named tiers. */
record ConfigSearch(List<String> words, Set<VisualPerformance.Impact> impacts, boolean valid) {
    static ConfigSearch parse(String query) {
        var words = new ArrayList<String>();
        var impacts = EnumSet.noneOf(VisualPerformance.Impact.class);
        boolean valid = true;
        var tokens = query.strip().toLowerCase(Locale.ROOT).split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.isEmpty()) continue;
            if (!token.startsWith("#")) {
                words.add(token);
                continue;
            }
            if (token.equals("#extra") && i + 1 < tokens.length && tokens[i + 1].equals("high")) {
                token = "#extrahigh";
                i++;
            }
            var impact = switch (token.replace("-", "").replace("_", "")) {
                case "#low" -> VisualPerformance.Impact.LOW;
                case "#medium" -> VisualPerformance.Impact.MEDIUM;
                case "#high" -> VisualPerformance.Impact.HIGH;
                case "#extrahigh" -> VisualPerformance.Impact.EXTRA_HIGH;
                default -> null;
            };
            if (impact == null) valid = false;
            else impacts.add(impact);
        }
        return new ConfigSearch(List.copyOf(words), Set.copyOf(impacts), valid);
    }

    boolean matches(String text, VisualPerformance.@Nullable Impact impact) {
        if (!valid || (!impacts.isEmpty() && (impact == null || !impacts.contains(impact)))) return false;
        String normalized = text.toLowerCase(Locale.ROOT);
        return words.stream().allMatch(normalized::contains);
    }
}

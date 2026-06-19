package me.neznamy.tab.shared.features.scoreboard;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.neznamy.tab.shared.config.file.ConfigurationSection;
import me.neznamy.tab.shared.features.PlaceholderManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Class for storing scoreboard configuration.
 */
@Getter
@RequiredArgsConstructor
public class ScoreboardConfiguration {

    @NotNull private final ConfigurationSection section;
    @NotNull private final String toggleCommand;
    private final boolean rememberToggleChoice;
    private final boolean hiddenByDefault;
    private final int joinDelay;
    @NotNull private final Map<String, ScoreboardDefinition> scoreboards;

    /**
     * Returns instance of this class created from given configuration section. If there are
     * issues in the configuration, console warns are printed.
     *
     * @param   section
     *          Configuration section to load from
     * @return  Loaded instance from given configuration section
     */
    public static ScoreboardConfiguration fromSection(@NotNull ConfigurationSection section) {
        // Check keys
        section.checkForUnknownKey(Arrays.asList("enabled", "toggle-command", "remember-toggle-choice", "hidden-by-default",
                "delay-on-join-milliseconds", "scoreboards"));

        // Load scoreboards
        ConfigurationSection scoreboardsSection = section.getConfigurationSection("scoreboards");
        Map<String, ScoreboardDefinition> scoreboards = new LinkedHashMap<>();
        for (Object scoreboard : scoreboardsSection.getKeys()) {
            String asString = scoreboard.toString();
            scoreboards.put(asString, ScoreboardDefinition.fromSection(asString, scoreboardsSection.getConfigurationSection(asString)));
        }

        // Check scoreboard chain for hanging scoreboards
        checkChain(section,scoreboards);

        return new ScoreboardConfiguration(
                section,
                section.getString("toggle-command", "/sb"),
                section.getBoolean("remember-toggle-choice", false),
                section.getBoolean("hidden-by-default", false),
                section.getInt("delay-on-join-milliseconds", 0),
                scoreboards
        );
    }

    private static void checkChain(@NotNull ConfigurationSection section, Map<String, ScoreboardDefinition> scoreboards) {
        String noConditionScoreboard = null;
        for (Map.Entry<String, ScoreboardDefinition> entry : scoreboards.entrySet()) {
            if (noConditionScoreboard != null) {
                section.startupWarn("Scoreboard \"" + noConditionScoreboard + "\" has no display condition set, however, there is" +
                        " another scoreboard in the chain (" + entry.getKey() + "). Scoreboards are checked from top to bottom" +
                        " until a scoreboard with meeting condition or no condition is found. Because of this, the scoreboard (" +
                        entry.getKey() + ") after the no-condition scoreboard (" + noConditionScoreboard + ") will never be displayed. " +
                        "Unless this is intentional to externally display the scoreboard (commands, API), this is a mistake.");
            } else if (entry.getValue().displayCondition == null) {
                noConditionScoreboard = entry.getKey();
            }
        }
    }

    /**
     * Class representing configuration of a specific scoreboard.
     */
    @Getter
    @RequiredArgsConstructor
    public static class ScoreboardDefinition {

        @Nullable private final String displayCondition;
        @NotNull private final String title;
        @NotNull private final List<String> lines;
        @NotNull private final Map<String, ScoreboardContent> languages;

        /**
         * Returns instance of this class created from given configuration section. If there are
         * issues in the configuration, console warns are printed.
         *
         * @param   name
         *          Name of the scoreboard
         * @param   section
         *          Configuration section to load from
         * @return  Loaded instance from given configuration section
         */
        public static ScoreboardDefinition fromSection(@NotNull String name, @NotNull ConfigurationSection section) {
            // Check keys
            section.checkForUnknownKey(Arrays.asList("display-condition", "title", "lines", "languages"));

            // Check if more than 15 lines are defined
            List<String> lines = section.getStringList("lines", Arrays.asList("scoreboard \"" +
                    name +"\" is missing \"lines\" property!", "did you forget to configure it or just your spacing is wrong?"));
            checkVisibleLines(name, section, lines);

            Map<String, ScoreboardContent> languages = new LinkedHashMap<>();
            ConfigurationSection languagesSection = section.getConfigurationSection("languages");
            for (Object key : languagesSection.getKeys()) {
                String language = key.toString().trim().toLowerCase(Locale.US);
                if (language.isEmpty()) {
                    languagesSection.startupWarn("Ignoring scoreboard language variant with empty language code.");
                    continue;
                }
                ScoreboardContent content = ScoreboardContent.fromSection(languagesSection.getConfigurationSection(key.toString()));
                if (content.getLines() != null) checkVisibleLines(name + ".languages." + language, section, content.getLines());
                languages.put(language, content);
            }

            return new ScoreboardDefinition(
                    section.getString("display-condition"),
                    section.getString("title", "<Title is not defined>"),
                    lines,
                    languages
            );
        }

        @NotNull
        public String getTitle(@Nullable String language) {
            ScoreboardContent content = getLocalizedContent(language);
            return content == null || content.getTitle() == null ? title : content.getTitle();
        }

        @NotNull
        public List<String> getLines(@Nullable String language) {
            ScoreboardContent content = getLocalizedContent(language);
            return content == null || content.getLines() == null ? lines : content.getLines();
        }

        public int getMaxLineCount() {
            int max = lines.size();
            for (ScoreboardContent content : languages.values()) {
                if (content.getLines() != null) max = Math.max(max, content.getLines().size());
            }
            return max;
        }

        @NotNull
        public String getLineTemplate(int index) {
            if (index < lines.size() && lines.get(index) != null) return lines.get(index);
            for (ScoreboardContent content : languages.values()) {
                List<String> localizedLines = content.getLines();
                if (localizedLines != null && index < localizedLines.size() && localizedLines.get(index) != null) {
                    return localizedLines.get(index);
                }
            }
            return "";
        }

        @Nullable
        private ScoreboardContent getLocalizedContent(@Nullable String language) {
            if (language == null) return null;
            return languages.get(language.trim().toLowerCase(Locale.US));
        }

        private static void checkVisibleLines(@NotNull String name, @NotNull ConfigurationSection section, @NotNull List<String> lines) {
            int alwaysVisibleLines = 0;
            for (String line : lines) {
                if (line == null) continue;
                String withoutPlaceholders = line;
                for (String placeholder : PlaceholderManagerImpl.detectPlaceholders(line)) {
                    withoutPlaceholders = withoutPlaceholders.replace(placeholder, "");
                }
                if (!withoutPlaceholders.isEmpty()) alwaysVisibleLines++;
            }
            if (alwaysVisibleLines > 15) {
                section.startupWarn(String.format("Scoreboard \"%s\" has %d defined lines, at least %d of which are permanently visible. " +
                                "However, the client only displays up to 15 lines, with any lines below them not being displayed.",
                        name, lines.size(), alwaysVisibleLines));
            }
        }
    }

    /**
     * Localized scoreboard content for one language.
     */
    @Getter
    @RequiredArgsConstructor
    public static class ScoreboardContent {

        @Nullable private final String title;
        @Nullable private final List<String> lines;

        @NotNull
        public static ScoreboardContent fromSection(@NotNull ConfigurationSection section) {
            section.checkForUnknownKey(Arrays.asList("title", "lines"));
            return new ScoreboardContent(section.getString("title"), section.getStringList("lines"));
        }
    }
}

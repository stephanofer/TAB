package me.neznamy.tab.shared.features.header;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import me.neznamy.tab.shared.config.file.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.Locale;

/**
 * Class storing header/footer configuration.
 */
@Getter
@RequiredArgsConstructor
public class HeaderFooterConfiguration {

    @NotNull private final ConfigurationSection section;

    /** Map of header/footer designs */
    @NotNull private final LinkedHashMap<String, HeaderFooterDesignDefinition> designs;

    /**
     * Returns instance of this class created from given configuration section. If there are
     * issues in the configuration, console warns are printed.
     *
     * @param   section
     *          Configuration section to load from
     * @return  Loaded instance from given configuration section
     */
    @NotNull
    public static HeaderFooterConfiguration fromSection(@NotNull ConfigurationSection section) {
        // Check keys
        section.checkForUnknownKey(Arrays.asList("enabled", "designs"));

        // Load designs
        LinkedHashMap<String, HeaderFooterDesignDefinition> designs = new LinkedHashMap<>();
        ConfigurationSection designsSection = section.getConfigurationSection("designs");
        for (Object key : designsSection.getKeys()) {
            designs.put(key.toString(), HeaderFooterDesignDefinition.fromSection(designsSection.getConfigurationSection(key.toString())));
        }

        // Check design chain for hanging designs
        checkChain(section, designs);

        return new HeaderFooterConfiguration(section, designs);
    }

    private static void checkChain(@NotNull ConfigurationSection section, Map<String, HeaderFooterDesignDefinition> scoreboards) {
        String noConditionDesign = null;
        for (Map.Entry<String, HeaderFooterDesignDefinition> entry : scoreboards.entrySet()) {
            if (noConditionDesign != null) {
                section.startupWarn("Header/footer design \"" + noConditionDesign + "\" has no display condition set, however, there is" +
                        " another design in the chain (" + entry.getKey() + "). Designs are checked from top to bottom" +
                        " until a design with meeting condition or no condition is found. Because of this, the design (" +
                        entry.getKey() + ") after the no-condition design (" + noConditionDesign + ") will never be displayed.");
            } else if (entry.getValue().displayCondition == null) {
                noConditionDesign = entry.getKey();
            }
        }
    }

    /**
     * Class storing a header-footer design.
     */
    @Getter
    @RequiredArgsConstructor
    public static class HeaderFooterDesignDefinition {

        @Nullable private final String displayCondition;
        @NonNull private final List<String> header;
        @NonNull private final List<String> footer;
        @NonNull private final Map<String, HeaderFooterContent> languages;

        /**
         * Returns instance of this class created from given configuration section. If there are
         * issues in the configuration, console warns are printed.
         *
         * @param   section
         *          Configuration section to load from
         * @return  Loaded instance from given configuration section
         */
        public static HeaderFooterDesignDefinition fromSection(@NotNull ConfigurationSection section) {
            // Check keys
            section.checkForUnknownKey(Arrays.asList("header", "footer", "display-condition", "languages"));

            Map<String, HeaderFooterContent> languages = new LinkedHashMap<>();
            ConfigurationSection languagesSection = section.getConfigurationSection("languages");
            for (Object key : languagesSection.getKeys()) {
                String language = key.toString().trim().toLowerCase(Locale.US);
                if (language.isEmpty()) {
                    languagesSection.startupWarn("Ignoring header/footer language variant with empty language code.");
                    continue;
                }
                languages.put(language, HeaderFooterContent.fromSection(languagesSection.getConfigurationSection(key.toString())));
            }

            return new HeaderFooterDesignDefinition(
                    section.getString("display-condition"),
                    section.getStringList("header", Collections.emptyList()),
                    section.getStringList("footer", Collections.emptyList()),
                    languages
            );
        }

        @NotNull
        public List<String> getHeader(@Nullable String language) {
            HeaderFooterContent content = getLocalizedContent(language);
            return content == null || content.getHeader() == null ? header : content.getHeader();
        }

        @NotNull
        public List<String> getFooter(@Nullable String language) {
            HeaderFooterContent content = getLocalizedContent(language);
            return content == null || content.getFooter() == null ? footer : content.getFooter();
        }

        @Nullable
        private HeaderFooterContent getLocalizedContent(@Nullable String language) {
            if (language == null) return null;
            return languages.get(language.trim().toLowerCase(Locale.US));
        }
    }

    /**
     * Localized header/footer content for one language.
     */
    @Getter
    @RequiredArgsConstructor
    public static class HeaderFooterContent {

        @Nullable private final List<String> header;
        @Nullable private final List<String> footer;

        @NotNull
        public static HeaderFooterContent fromSection(@NotNull ConfigurationSection section) {
            section.checkForUnknownKey(Arrays.asList("header", "footer"));
            return new HeaderFooterContent(section.getStringList("header"), section.getStringList("footer"));
        }
    }
}

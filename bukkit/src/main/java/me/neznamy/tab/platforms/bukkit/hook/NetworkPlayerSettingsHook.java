package me.neznamy.tab.platforms.bukkit.hook;

import com.stephanofer.networkplayersettings.api.PlayerSettingsService;
import com.stephanofer.networkplayersettings.api.SettingKey;
import com.stephanofer.networkplayersettings.event.PlayerSettingChangeEvent;
import com.stephanofer.networkplayersettings.event.PlayerSettingsReadyEvent;
import me.neznamy.tab.platforms.bukkit.platform.BukkitPlatform;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants;
import me.neznamy.tab.shared.chat.TabTextColor;
import me.neznamy.tab.shared.chat.component.TabTextComponent;
import me.neznamy.tab.shared.features.header.HeaderFooter;
import me.neznamy.tab.shared.features.scoreboard.ScoreboardManagerImpl;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional Bukkit-only integration with NetworkPlayerSettings.
 */
public class NetworkPlayerSettingsHook implements Listener {

    @NotNull private final BukkitPlatform platform;
    @NotNull private final Map<UUID, String> languageCache = new ConcurrentHashMap<>();
    @Nullable private PlayerSettingsService service;
    private boolean missingServiceWarned;

    public NetworkPlayerSettingsHook(@NotNull JavaPlugin plugin, @NotNull BukkitPlatform platform) {
        this.platform = platform;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        PlayerSettingsService settings = service();
        if (settings != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (settings.isReady(player.getUniqueId())) {
                    languageCache.put(player.getUniqueId(), settings.resolvedLanguage(player).code());
                }
            }
        }
    }

    @Nullable
    public String getLanguageCode(@NotNull TabPlayer player) {
        return languageCache.get(player.getUniqueId());
    }

    @EventHandler
    public void onSettingsReady(@NotNull PlayerSettingsReadyEvent event) {
        languageCache.put(event.player().getUniqueId(), event.resolvedLanguage().code());
        refresh(event.player().getUniqueId());
    }

    @EventHandler
    public void onSettingChange(@NotNull PlayerSettingChangeEvent event) {
        if (event.settingKey() == SettingKey.LANGUAGE) {
            String language = event.newResolvedValue();
            if (language == null || language.isEmpty()) {
                languageCache.remove(event.playerId());
            } else {
                languageCache.put(event.playerId(), language);
            }
            refresh(event.playerId());
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        languageCache.remove(event.getPlayer().getUniqueId());
    }

    private void refresh(@NotNull UUID playerId) {
        TAB tab = TAB.getInstance();
        if (tab == null) return;
        TabPlayer player = tab.getPlayer(playerId);
        if (player == null) return;

        HeaderFooter headerFooter = tab.getFeatureManager().getFeature(TabConstants.Feature.HEADER_FOOTER);
        if (headerFooter != null) headerFooter.refreshLanguage(player);

        ScoreboardManagerImpl scoreboard = tab.getFeatureManager().getFeature(TabConstants.Feature.SCOREBOARD);
        if (scoreboard != null) scoreboard.refreshLanguage(player);
    }

    @Nullable
    private PlayerSettingsService service() {
        if (service != null) return service;
        service = Bukkit.getServicesManager().load(PlayerSettingsService.class);
        if (service == null && !missingServiceWarned) {
            missingServiceWarned = true;
            platform.logWarn(new TabTextComponent("NetworkPlayerSettings is installed but PlayerSettingsService is not registered yet. Localized TAB content will use defaults until the service is available.", TabTextColor.RED));
        }
        return service;
    }
}

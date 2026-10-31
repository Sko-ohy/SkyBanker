package me.skoohy.skybanker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks Modrinth for a newer version of the plugin.
 *
 * - Runs fully ASYNC (never blocks the main thread).
 * - One tiny HTTP request at every server start, then once every N hours (default 24).
 * - If anything fails (no internet, Modrinth down) it just skips quietly.
 * - Update found: one console warning + a clickable chat notice for admins
 *   (permission skybanker.admin) when they join.
 */
public class UpdateChecker implements Listener {

    private static final String CFG = "update-checker";
    private static final String PROJECT_ID = "GAP9zjCv";   // fixed on purpose - not editable in config
    private static final long INTERVAL_HOURS = 24;
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private final SkyBanker plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // Written by the async thread, read by the main thread
    private volatile String latestVersion = null;   // null = up to date / unknown
    private volatile String downloadUrl = null;
    private volatile String lastLoggedVersion = null;

    public UpdateChecker(SkyBanker plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // =====================================================================
    // Start
    // =====================================================================

    public void start() {
        if (!cfg().getBoolean(CFG + ".enabled", true)) return;

        if (cfg().getBoolean(CFG + ".notify-admins", true)) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }

        long hours = INTERVAL_HOURS;
        // first check 5 seconds after start (server finishes loading first), then every N hours
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> check(PROJECT_ID), 100L, hours * 60L * 60L * 20L);
    }

    // =====================================================================
    // The check (async thread)
    // =====================================================================

    private void check(String project) {
        try {
            String current = plugin.getDescription().getVersion();
            boolean allowPre = false; // only full releases

            URI uri = URI.create("https://api.modrinth.com/v2/project/"
                    + URLEncoder.encode(project, StandardCharsets.UTF_8) + "/version");

            // Modrinth asks every app to send a descriptive User-Agent
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "skoohy/SkyBanker/" + current)
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                debug("Modrinth answered with status " + response.statusCode());
                return;
            }

            // Modrinth returns the newest version first
            JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
            String remote = null;
            String versionId = null;
            for (JsonElement el : versions) {
                JsonObject v = el.getAsJsonObject();
                String type = v.has("version_type") ? v.get("version_type").getAsString() : "release";
                if (!allowPre && !type.equalsIgnoreCase("release")) continue;
                remote = v.get("version_number").getAsString();
                versionId = v.get("id").getAsString();
                break;
            }

            if (remote == null) return;

            if (isNewer(remote, current)) {
                latestVersion = remote;
                downloadUrl = "https://modrinth.com/plugin/" + project + "/version/" + versionId;

                if (!remote.equals(lastLoggedVersion)) {
                    lastLoggedVersion = remote;
                    plugin.getLogger().warning("A new version is available: " + remote
                            + " (you have " + current + ") - " + downloadUrl);

                    // tell admins who are already online (only when a NEW version is found)
                    if (cfg().getBoolean(CFG + ".notify-admins", true)) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (p.hasPermission("skybanker.admin")) sendNotice(p);
                            }
                        });
                    }
                }
            } else {
                latestVersion = null;
                downloadUrl = null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // No internet, bad JSON, wrong project name... never spam the console
            debug("Update check failed: " + e.getMessage());
        }
    }

    private void debug(String text) {
        if (cfg().getBoolean("settings.debug-mode", false)) {
            plugin.getLogger().info("[UpdateChecker] " + text);
        }
    }

    // =====================================================================
    // Version compare: 1.10.0 is newer than 1.9.0 (numbers, not text)
    // =====================================================================

    private static List<Integer> numbers(String version) {
        List<Integer> out = new ArrayList<>();
        Matcher m = NUMBER.matcher(version);
        while (m.find()) {
            try {
                out.add(Integer.parseInt(m.group()));
            } catch (NumberFormatException ignored) {
                out.add(0);
            }
        }
        return out;
    }

    static boolean isNewer(String remote, String current) {
        List<Integer> r = numbers(remote);
        List<Integer> c = numbers(current);
        int len = Math.max(r.size(), c.size());
        for (int i = 0; i < len; i++) {
            int a = i < r.size() ? r.get(i) : 0;
            int b = i < c.size() ? c.get(i) : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    // =====================================================================
    // Chat notice (main thread)
    // =====================================================================

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (latestVersion == null || !player.hasPermission("skybanker.admin")) return;

        // small delay so the notice isn't buried under join messages
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) sendNotice(player);
        }, 60L);
    }

    private String msg(String key, String def) {
        String m = BankManager.getMessage(key);
        return m.contains("Missing message") ? BankManager.colorize(def) : m;
    }

    private void sendNotice(Player player) {
        String latest = latestVersion;
        String url = downloadUrl;
        if (latest == null || url == null) return;

        String current = plugin.getDescription().getVersion();
        String body = msg("update-available",
                "&6&m                                        &r\n"
                        + "&6&lSkyBanker &8» &eA new update is available!\n"
                        + "&7Your version: &c%current% &8→ &7New version: &a%latest%")
                .replace("%current%", current)
                .replace("%latest%", latest);

        for (String line : body.split("\n")) {
            player.sendMessage(line);
        }

        String click = msg("update-click", "&a&l[Click to download on Modrinth]");
        String hover = msg("update-hover", "&7Open &e%url%").replace("%url%", url);
        String end = msg("update-footer", "&6&m                                        &r");

        Component link = LegacyComponentSerializer.legacySection().deserialize(click)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection().deserialize(hover)));
        player.sendMessage(link);
        player.sendMessage(end);
    }
}

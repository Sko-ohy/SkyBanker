package me.skoohy.skybanker;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player settings (currently: which bank sounds are muted).
 *
 * Performance design:
 *  - Settings are kept in an in-memory map, so playing a sound NEVER
 *    touches the database (BankerSound only reads the map).
 *  - Loaded async on join, removed on quit.
 *  - Saved async when a player toggles something.
 *  - Stored as ONE integer bitmask of the sounds that are OFF, so the
 *    default (0) means "everything on" and new sound types can be added
 *    later without migrating old data.
 */
public class PlayerSettings implements Listener {

    public enum SoundType {
        MASTER, INTEREST, DEPOSIT, WITHDRAW, UPGRADE, ERROR, CLICK;

        public int bit() {
            return 1 << ordinal();
        }
    }

    private static final Map<UUID, Integer> OFF_MASK = new ConcurrentHashMap<>();

    /** True if this single sound type is switched on (ignores the master switch). */
    public static boolean isSoundOn(UUID uuid, SoundType type) {
        Integer mask = OFF_MASK.get(uuid);
        return mask == null || (mask & type.bit()) == 0;
    }

    /** True if this sound should actually play: master ON and the type ON. */
    public static boolean shouldPlay(Player player, SoundType type) {
        Integer mask = OFF_MASK.get(player.getUniqueId());
        if (mask == null) return true; // not loaded yet -> default is "on"
        return (mask & (SoundType.MASTER.bit() | type.bit())) == 0;
    }

    /** Flips one sound type. Returns the NEW state (true = on). */
    public static boolean toggle(Player player, SoundType type) {
        UUID uuid = player.getUniqueId();
        int newMask = OFF_MASK.merge(uuid, type.bit(), (old, bit) -> old ^ bit);
        saveAsync(uuid, newMask);
        return (newMask & type.bit()) == 0;
    }

    private static void saveAsync(UUID uuid, int fallback) {
        SkyBanker plugin = SkyBanker.getInstance();
        if (plugin == null || !plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Always write the newest value, so rapid toggles can't save an old one.
            Integer latest = OFF_MASK.get(uuid);
            SkyBankerDatabase.setSoundOffMask(uuid, latest != null ? latest : fallback);
        });
    }

    private static void loadAsync(UUID uuid) {
        SkyBanker plugin = SkyBanker.getInstance();
        if (plugin == null || !plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int mask = SkyBankerDatabase.getSoundOffMask(uuid);
            if (Bukkit.getPlayer(uuid) != null) {
                OFF_MASK.putIfAbsent(uuid, mask);
            }
        });
    }

    /** Call once in onEnable so players online during a /reload are covered. */
    public static void loadOnlinePlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            loadAsync(p.getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadAsync(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        OFF_MASK.remove(event.getPlayer().getUniqueId());
    }
}

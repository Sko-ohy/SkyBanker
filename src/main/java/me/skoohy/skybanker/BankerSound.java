package me.skoohy.skybanker;

import me.skoohy.skybanker.PlayerSettings.SoundType;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Every bank sound goes through play(), which checks the player's
 * sound settings (an in-memory lookup - no database access).
 */
public class BankerSound {

    private static void play(Player player, SoundType type) {
        if (!PlayerSettings.shouldPlay(player, type)) return;
        player.playSound(player.getLocation(), soundOf(type), 1.0f, pitchOf(type));
    }

    private static Sound soundOf(SoundType type) {
        return switch (type) {
            case INTEREST, UPGRADE -> Sound.ENTITY_PLAYER_LEVELUP;
            case ERROR -> Sound.ENTITY_VILLAGER_NO;
            default -> Sound.UI_BUTTON_CLICK;
        };
    }

    private static float pitchOf(SoundType type) {
        return switch (type) {
            case DEPOSIT -> 1.75f;
            case WITHDRAW -> 2.0f;
            case INTEREST, UPGRADE -> 1.2f;
            case ERROR -> 0.9f;
            default -> 1.2f;
        };
    }

    public static void playDeposit(Player player) {
        play(player, SoundType.DEPOSIT);
    }

    public static void playWithdraw(Player player) {
        play(player, SoundType.WITHDRAW);
    }

    public static void playUpgrade(Player player) {
        play(player, SoundType.UPGRADE);
    }

    /** Sound for the automatic interest payout (has its own toggle). */
    public static void playInterest(Player player) {
        play(player, SoundType.INTEREST);
    }

    public static void playError(Player player) {
        play(player, SoundType.ERROR);
    }

    public static void playClick(Player player) {
        play(player, SoundType.CLICK);
    }

    /** Plays a sound even if it is muted - used by the "preview" button in the sound menu. */
    public static void preview(Player player, SoundType type) {
        player.playSound(player.getLocation(), soundOf(type), 1.0f, pitchOf(type));
    }
}

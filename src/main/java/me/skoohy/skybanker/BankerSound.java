package me.skoohy.skybanker;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class BankerSound {

    public static void playDeposit(Player player) {
        // High-pitched experience orb pickup sound mimicking SkyBlock coin collection
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.75f);
    }

    public static void playWithdraw(Player player) {
        // Clean high-pitched pling note for successful cash withdrawal
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 2.0f);
    }

    public static void playUpgrade(Player player) {
        // Level up chime for purchasing milestone bank tier upgrades
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    public static void playError(Player player) {
        // Low-pitched villager rejection sound for full banks or insufficient funds
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.9f);
    }

    public static void playClick(Player player) {
        // Standard user interface button click feedback matching inventory navigation
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }
}
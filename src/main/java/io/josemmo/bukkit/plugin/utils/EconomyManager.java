package io.josemmo.bukkit.plugin.utils;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;

public class EconomyManager {
    /**
     * Deduct Money
     *
     * @param player Target player instance
     * @param amount Amount deducted (positive)
     * @return Success or not
     * @author ObcbO
     */
    public static boolean deductMoney(Player player, double amount) {
        YamipaPlugin plugin = YamipaPlugin.getInstance();
        Economy econ = plugin.getVaultEconomy();

        double nowHave = econ.getBalance(player);
        if (nowHave >= amount) {
            EconomyResponse rp = econ.withdrawPlayer(player, amount);
            return rp.transactionSuccess();
        } else return false;
    }
}

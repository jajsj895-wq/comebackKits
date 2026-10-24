package com.senin.comebackkits;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class ComebackKits extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("comebackKits açıldı!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player p) {
            p.sendMessage("comebackKits çalışıyor!");
        }
        return true;
    }
}

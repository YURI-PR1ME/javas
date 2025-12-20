package com.yourname.shopplugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class CreditHook {
    
    public static boolean hasEnoughCredits(Player player, int amount) {
        try {
            // 通过反射获取原插件的CreditManager
            Class<?> creditPluginClass = Class.forName("com.yourname.creditplugin.CreditPlugin");
            Object creditPluginInstance = creditPluginClass.getMethod("getInstance").invoke(null);
            Object creditManager = creditPluginClass.getMethod("getCreditManager").invoke(creditPluginInstance);
            
            Class<?> creditManagerClass = creditManager.getClass();
            int credits = (int) creditManagerClass.getMethod("getCredits", Player.class).invoke(creditManager, player);
            
            return credits >= amount;
        } catch (Exception e) {
            Bukkit.getLogger().warning("无法连接到信用点系统: " + e.getMessage());
            return false;
        }
    }
    
    public static boolean chargePlayer(Player player, int amount) {
        try {
            // 通过反射获取原插件的CreditManager
            Class<?> creditPluginClass = Class.forName("com.yourname.creditplugin.CreditPlugin");
            Object creditPluginInstance = creditPluginClass.getMethod("getInstance").invoke(null);
            Object creditManager = creditPluginClass.getMethod("getCreditManager").invoke(creditPluginInstance);
            
            Class<?> creditManagerClass = creditManager.getClass();
            return (boolean) creditManagerClass.getMethod("removeCredits", Player.class, int.class)
                    .invoke(creditManager, player, amount);
        } catch (Exception e) {
            Bukkit.getLogger().warning("无法扣除信用点: " + e.getMessage());
            return false;
        }
    }
    
    public static int getPlayerCredits(Player player) {
        try {
            Class<?> creditPluginClass = Class.forName("com.yourname.creditplugin.CreditPlugin");
            Object creditPluginInstance = creditPluginClass.getMethod("getInstance").invoke(null);
            Object creditManager = creditPluginClass.getMethod("getCreditManager").invoke(creditPluginInstance);
            
            Class<?> creditManagerClass = creditManager.getClass();
            return (int) creditManagerClass.getMethod("getCredits", Player.class).invoke(creditManager, player);
        } catch (Exception e) {
            Bukkit.getLogger().warning("无法获取玩家信用点: " + e.getMessage());
            return 0;
        }
    }
}

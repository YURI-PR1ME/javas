// CreditIntegration.java
package com.yourname.nethercontrol;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class CreditIntegration {
    
    private boolean creditAvailable = false;
    private Object creditManager;
    private Method getCreditsMethod;
    private Method isInNetherMethod;
    private Method findSafeLocationMethod;
    
    public CreditIntegration() {
        setupCreditIntegration();
    }
    
    private void setupCreditIntegration() {
        try {
            Plugin creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin == null || !creditPlugin.isEnabled()) {
                NetherControlPlugin.getInstance().getLogger().warning("❌ CreditPlugin未找到或未启用");
                return;
            }
            
            NetherControlPlugin.getInstance().getLogger().info("✅ 检测到CreditPlugin，正在初始化集成...");
            
            // 尝试多种方法获取CreditManager实例
            Object creditManagerInstance = null;
            
            // 方法1: 通过getCreditManager方法
            try {
                Method getCreditManagerMethod = creditPlugin.getClass().getMethod("getCreditManager");
                creditManagerInstance = getCreditManagerMethod.invoke(creditPlugin);
                NetherControlPlugin.getInstance().getLogger().info("✅ 通过getCreditManager方法获取CreditManager");
            } catch (Exception e) {
                NetherControlPlugin.getInstance().getLogger().warning("❌ 通过getCreditManager方法获取失败: " + e.getMessage());
            }
            
            // 方法2: 通过CreditPlugin.getInstance()
            if (creditManagerInstance == null) {
                try {
                    Method getInstanceMethod = creditPlugin.getClass().getMethod("getInstance");
                    Object creditPluginInstance = getInstanceMethod.invoke(null);
                    Method getCreditManagerMethod = creditPluginInstance.getClass().getMethod("getCreditManager");
                    creditManagerInstance = getCreditManagerMethod.invoke(creditPluginInstance);
                    NetherControlPlugin.getInstance().getLogger().info("✅ 通过getInstance方法获取CreditManager");
                } catch (Exception e) {
                    NetherControlPlugin.getInstance().getLogger().warning("❌ 通过getInstance方法获取失败: " + e.getMessage());
                }
            }
            
            // 方法3: 直接通过字段获取
            if (creditManagerInstance == null) {
                try {
                    java.lang.reflect.Field creditManagerField = creditPlugin.getClass().getDeclaredField("creditManager");
                    creditManagerField.setAccessible(true);
                    creditManagerInstance = creditManagerField.get(creditPlugin);
                    NetherControlPlugin.getInstance().getLogger().info("✅ 通过反射字段获取CreditManager");
                } catch (Exception e) {
                    NetherControlPlugin.getInstance().getLogger().warning("❌ 通过反射字段获取失败: " + e.getMessage());
                }
            }
            
            if (creditManagerInstance == null) {
                NetherControlPlugin.getInstance().getLogger().severe("❌ 无法获取CreditManager实例");
                return;
            }
            
            this.creditManager = creditManagerInstance;
            
            // 获取需要的方法
            try {
                getCreditsMethod = creditManager.getClass().getMethod("getCredits", Player.class);
                isInNetherMethod = creditManager.getClass().getMethod("isInNether", Player.class);
                findSafeLocationMethod = creditManager.getClass().getMethod("findSafeLocation", World.class, Location.class);
                
                // 测试方法是否可用
                Player testPlayer = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
                if (testPlayer != null) {
                    getCreditsMethod.invoke(creditManager, testPlayer);
                }
                
                creditAvailable = true;
                NetherControlPlugin.getInstance().getLogger().info("✅ 信用点系统集成初始化成功");
                
            } catch (Exception e) {
                NetherControlPlugin.getInstance().getLogger().severe("❌ 信用点方法获取失败: " + e.getMessage());
                creditAvailable = false;
            }
            
        } catch (Exception e) {
            creditAvailable = false;
            NetherControlPlugin.getInstance().getLogger().severe("❌ 信用点系统集成初始化失败: " + e.getMessage());
        }
    }
    
    public boolean isCreditAvailable() {
        return creditAvailable;
    }
    
    public int getPlayerCredits(Player player) {
        if (!creditAvailable || creditManager == null) return 0;
        
        try {
            Object result = getCreditsMethod.invoke(creditManager, player);
            return result instanceof Integer ? (int) result : 0;
        } catch (Exception e) {
            NetherControlPlugin.getInstance().getLogger().warning("❌ 获取玩家信用点失败: " + e.getMessage());
            return 0;
        }
    }
    
    public boolean isPlayerInNether(Player player) {
        if (!creditAvailable || creditManager == null) return false;
        
        try {
            Object result = isInNetherMethod.invoke(creditManager, player);
            return result instanceof Boolean ? (boolean) result : false;
        } catch (Exception e) {
            NetherControlPlugin.getInstance().getLogger().warning("❌ 检查玩家位置失败: " + e.getMessage());
            return false;
        }
    }
    
    public Location findSafeLocation(World world, Location center) {
        if (!creditAvailable || creditManager == null) return center;
        
        try {
            Object result = findSafeLocationMethod.invoke(creditManager, world, center);
            return result instanceof Location ? (Location) result : center;
        } catch (Exception e) {
            NetherControlPlugin.getInstance().getLogger().warning("❌ 寻找安全位置失败: " + e.getMessage());
            return center;
        }
    }
    
    /**
     * 检查所有玩家的信用点状态，并根据规则进行传送
     */
    public void checkAllPlayers() {
        if (!creditAvailable) return;
        
        NetherControlManager controlManager = NetherControlPlugin.getInstance().getControlManager();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) continue;
            
            try {
                int credits = getPlayerCredits(player);
                boolean inNether = isPlayerInNether(player);
                
                // 规则1: 信用点<0且在主世界 -> 传送回地狱（不论地狱门状态）
                if (!inNether && credits < 0) {
                    teleportToNether(player);
                    player.sendMessage(ChatColor.RED + "🔥 由于你的信用点为负数，你被强制传送回地狱！");
                }
                // 规则2: 地狱门锁定状态下，信用点>0且在地狱 -> 传送回主世界
                else if (!controlManager.isUnlocked() && inNether && credits > 0) {
                    teleportToOverworld(player);
                    player.sendMessage(ChatColor.GREEN + "✨ 由于地狱门封锁且你的信用点为正数，你被自动传送回主世界");
                }
            } catch (Exception e) {
                NetherControlPlugin.getInstance().getLogger().warning("❌ 检查玩家 " + player.getName() + " 时出错: " + e.getMessage());
            }
        }
    }
    
    private void teleportToOverworld(Player player) {
        World overworld = Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElse(null);
        
        if (overworld != null) {
            Location safeLocation = findSafeLocation(overworld, overworld.getSpawnLocation());
            player.teleport(safeLocation);
        }
    }
    
    private void teleportToNether(Player player) {
        World nether = Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.NETHER)
                .findFirst()
                .orElse(null);
        
        if (nether != null) {
            Location safeLocation = findSafeLocation(nether, nether.getSpawnLocation());
            player.teleport(safeLocation);
        }
    }
}

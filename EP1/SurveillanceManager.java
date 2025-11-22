package com.yourname.creditsurveillance;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public class SurveillanceManager {
    
    private final CreditSurveillance plugin;
    
    public SurveillanceManager(CreditSurveillance plugin) {
        this.plugin = plugin;
    }
    
    // 获取玩家信用点（通过反射调用主插件）
    public int getPlayerCredits(Player player) {
        try {
            // 获取主插件的CreditManager
            Class<?> creditPluginClass = plugin.getCreditPlugin().getClass();
            Method getCreditManagerMethod = creditPluginClass.getMethod("getCreditManager");
            Object creditManager = getCreditManagerMethod.invoke(plugin.getCreditPlugin());
            
            // 调用getCredits方法
            Method getCreditsMethod = creditManager.getClass().getMethod("getCredits", Player.class);
            return (int) getCreditsMethod.invoke(creditManager, player);
            
        } catch (Exception e) {
            plugin.getLogger().warning("无法获取玩家 " + player.getName() + " 的信用点: " + e.getMessage());
            return 5; // 默认返回5点
        }
    }
    
    // 更新玩家监管状态
    public void updatePlayerSurveillance(Player player) {
        int credits = getPlayerCredits(player);
        PlayerSurveillanceData data = plugin.getPlayerData(player);
        
        // 信用点≥5：移除所有监管者
        if (credits >= 5) {
            data.removeAllGuardians();
            return;
        }
        
        // 信用点<2：需要3个监管者，小范围
        if (credits < 2) {
            data.setRequiredGuardians(3);
            // 第二阶段：信用点<2，使用更小的监管半径
            // 传送条件：距离≥64格时传送到32-56格范围
            data.setSurveillanceRadius(64.0); // 触发传送的距离
            data.setTeleportMinDistance(32.0); // 传送最小距离
            data.setTeleportMaxDistance(56.0); // 传送最大距离
        }
        // 信用点<4：需要1个监管者，大范围
        else if (credits < 4) {
            data.setRequiredGuardians(1);
            // 第一阶段：信用点<4，使用较大的监管半径
            // 传送条件：距离≥90格时传送到48-80格范围
            data.setSurveillanceRadius(90.0); // 触发传送的距离
            data.setTeleportMinDistance(48.0); // 传送最小距离
            data.setTeleportMaxDistance(80.0); // 传送最大距离
        }
        // 信用点4-5：不需要监管者
        else {
            data.setRequiredGuardians(0);
        }
        
        // 更新监管者数量
        data.updateGuardianCount();
    }
    
    // 处理玩家击杀（减少1个监管者）
    public void handlePlayerKill(Player killer) {
        PlayerSurveillanceData data = plugin.getPlayerData(killer);
        
        // 只有当有监管者时才减少
        if (data.getCurrentGuardianCount() > 0) {
            int currentRequired = data.getRequiredGuardians();
            // 确保不会减少到低于当前信用点要求的数量
            int newRequired = Math.max(data.calculateRequiredFromCredits(getPlayerCredits(killer)), 
                                     data.getCurrentGuardianCount() - 1);
            
            data.setRequiredGuardians(newRequired);
            data.updateGuardianCount();
            
            // 更新消息为英文
            killer.sendMessage("§c⚡ Your actions have drawn the attention of Apocolyps! Surveillance reduced to " + newRequired);
        }
    }
}

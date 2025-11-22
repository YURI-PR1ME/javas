// [file name]: DeathCrownManager.java
package com.yourname.deathcrown;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.Arrays;
import java.util.UUID;

public class DeathCrownManager {
    
    private final DeathCrownPlugin plugin;
    private final NamespacedKey crownKey;
    
    public DeathCrownManager(DeathCrownPlugin plugin) {
        this.plugin = plugin;
        this.crownKey = new NamespacedKey(plugin, "death_crown");
    }
    
    /**
     * 创建破碎王冠物品
     */
    public ItemStack createDeathCrown() {
        ItemStack crown = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = crown.getItemMeta();
        
        // 设置名称和Lore
        meta.setDisplayName("§8破碎王冠");
        meta.setLore(Arrays.asList(
            "§8« §7远古王权的残骸 §8»",
            "",
            "§7右键使用召唤§4溺尸王§7",
            "§7每个世界只能使用一次",
            "",
            "§c⚠ 警告: 这将召唤强大的Boss!",
            "§c请确保做好充分准备!"
        ));
        
        // 添加附魔光效
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        
        // 设置耐久度受损，体现"破碎"的感觉
        crown.setDurability((short) (crown.getType().getMaxDurability() * 0.7));
        
        // 设置持久化数据，标记为死亡王冠
        meta.getPersistentDataContainer().set(crownKey, PersistentDataType.BYTE, (byte) 1);
        
        crown.setItemMeta(meta);
        return crown;
    }
    
    /**
     * 检查物品是否是破碎王冠
     */
    public boolean isDeathCrown(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_HELMET || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(crownKey, PersistentDataType.BYTE);
    }
    
    /**
     * 使用破碎王冠
     */
    public boolean useDeathCrown(Player player) {
        UUID worldId = player.getWorld().getUID();
        
        // 检查世界是否已经使用过王冠
        if (plugin.isWorldUsed(worldId)) {
            player.sendMessage("§c❌ 这个世界已经使用过破碎王冠了!");
            player.sendMessage("§7如需重置，管理员可使用 §6/deathcrown reset §7命令");
            return false;
        }
        
        Location playerLoc = player.getLocation();
        
        try {
            // 1. 召唤溺尸王
            if (!summonDrownedKing(player, playerLoc)) {
                player.sendMessage("§c❌ 召唤溺尸王失败! 请检查溺尸王插件是否正常运行");
                return false;
            }
            
            // 2. 标记世界为已使用
            plugin.markWorldAsUsed(worldId);
            
            // 3. 移除王冠
            removeCrownFromPlayer(player);
            
            // 4. 播放特效和音效
            playActivationEffects(player);
            
            player.sendMessage("§4⚡ 破碎王冠的力量已经释放!");
            player.sendMessage("§c⚠ 溺尸王已被召唤，准备战斗!");
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("使用破碎王冠时出错: " + e.getMessage());
            player.sendMessage("§c❌ 使用破碎王冠时发生错误!");
            return false;
        }
    }
    
    /**
     * 通过命令召唤溺尸王
     */
    private boolean summonDrownedKing(Player player, Location location) {
        try {
            // 尝试通过溺尸王插件的命令生成
            String command = String.format("drownedking spawn %s", player.getName());
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            
            if (!success) {
                // 备用方案：直接调用API（如果可用）
                plugin.getLogger().warning("命令召唤失败，尝试备用方案...");
                return tryAlternativeSpawn(player, location);
            }
            
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("召唤溺尸王失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 备用召唤方案
     */
    private boolean tryAlternativeSpawn(Player player, Location location) {
        try {
            // 这里可以尝试通过反射调用溺尸王插件的API
            // 由于插件结构未知，这里使用命令作为主要方式
            plugin.getLogger().warning("备用方案暂时不可用");
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 从玩家处移除王冠
     */
    private void removeCrownFromPlayer(Player player) {
        // 检查主手
        if (isDeathCrown(player.getInventory().getItemInMainHand())) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        
        // 检查副手
        if (isDeathCrown(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
            return;
        }
        
        // 检查头盔
        if (isDeathCrown(player.getInventory().getHelmet())) {
            player.getInventory().setHelmet(null);
            return;
        }
        
        // 检查背包
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isDeathCrown(item)) {
                player.getInventory().setItem(i, null);
                return;
            }
        }
    }
    
    /**
     * 播放激活特效
     */
    private void playActivationEffects(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        
        // 音效
        world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        
        // 粒子效果
        for (int i = 0; i < 20; i++) {
            double angle = 2 * Math.PI * i / 20;
            double x = Math.cos(angle) * 2;
            double z = Math.sin(angle) * 2;
            
            Location particleLoc = loc.clone().add(x, 1, z);
            world.spawnParticle(org.bukkit.Particle.FLAME, particleLoc, 3, 0.1, 0.1, 0.1, 0.02);
            world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, particleLoc, 2, 0.1, 0.1, 0.1, 0.01);
        }
        
        // 闪电效果（不造成伤害）
        world.strikeLightningEffect(loc.clone().add(2, 0, 2));
        world.strikeLightningEffect(loc.clone().add(-2, 0, 2));
        world.strikeLightningEffect(loc.clone().add(2, 0, -2));
        world.strikeLightningEffect(loc.clone().add(-2, 0, -2));
    }
    
    /**
     * 给玩家破碎王冠
     */
    public void giveDeathCrownToPlayer(Player player) {
        ItemStack crown = createDeathCrown();
        
        if (player.getInventory().addItem(crown).isEmpty()) {
            player.sendMessage("§8👑 你获得了 §8破碎王冠§6!");
            player.sendMessage("§7右键使用召唤§4溺尸王§7");
            
            // 播放获得音效
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.8f);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f);
        } else {
            // 背包已满，掉落物品
            player.getWorld().dropItemNaturally(player.getLocation(), crown);
            player.sendMessage("§6💡 背包已满，破碎王冠已掉落在地面上");
        }
    }
}

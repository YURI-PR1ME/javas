package com.example.endsword;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public class EndSwordPlugin extends JavaPlugin {
    
    private static EndSwordPlugin instance;
    private NamespacedKey recipeKey;
    private PlayerHitListener hitListener;
    
    @Override
    public void onEnable() {
        instance = this;
        this.recipeKey = new NamespacedKey(this, "end_sword");
        this.hitListener = new PlayerHitListener();
        
        // 初始化物品系统
        EndSwordItem.initialize(this);
        
        // 注册合成配方
        registerRecipe();
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(hitListener, this);
        // 注册命令
        getCommand("endsword").setExecutor(new EndSwordCommand());
        
        // 启动持续效果任务
        startEffectTask();
        
        getLogger().info("终末之剑插件已启用!");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("终末之剑插件已禁用!");
    }
    
    private void registerRecipe() {
        ItemStack endSword = EndSwordItem.createEndSword();
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, endSword);
        
        // 修改合成表：类似火把，龙蛋在上，木棍在下
        recipe.shape("D", "S");
        recipe.setIngredient('D', Material.DRAGON_EGG);
        recipe.setIngredient('S', Material.STICK);
        
        try {
            Bukkit.addRecipe(recipe);
            getLogger().info("终末之剑合成配方注册成功!");
        } catch (Exception e) {
            getLogger().warning("注册合成配方时出错: " + e.getMessage());
        }
    }
    
    private void startEffectTask() {
        // 每2秒检查一次玩家手持物品并给予效果（40 ticks = 2秒）
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                ItemStack offHand = player.getInventory().getItemInOffHand();
                
                // 检查主手或副手是否持有终末之剑
                if (EndSwordItem.isEndSword(mainHand) || EndSwordItem.isEndSword(offHand)) {
                    // 给予状态效果 - 力量改为2级（amplifier=1）
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.HASTE, 60, 0, true, false)); // 急迫 I
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.STRENGTH, 60, 1, true, false)); // 力量 II (amplifier=1)
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SPEED, 60, 0, true, false)); // 速度 I
                }
            }
        }, 0L, 40L); // 立即开始，每40tick（2秒）执行一次
    }
    
    public static EndSwordPlugin getInstance() {
        return instance;
    }
    
    public PlayerHitListener getHitListener() {
        return hitListener;
    }
}

package com.yourdomain.endsword;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class AbilityListener implements Listener {

    private final ItemManager itemManager;
    private final Random random = new Random();
    private final EndSword plugin; 

    // 存储右键技能冷却 <玩家UUID, 冷却结束时间戳>
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    // 存储暴击计数 <攻击者UUID, <目标UUID, 计数>>
    private final Map<UUID, Map<UUID, Integer>> critCounts = new HashMap<>();

    public AbilityListener(EndSword plugin, ItemManager itemManager) {
        this.plugin = plugin; 
        this.itemManager = itemManager;
    }

    /**
     * 监听右键：雷暴技能
     */
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 必须是右键
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // 必须手持终末之剑
        if (!itemManager.isEndSword(item)) {
            return;
        }

        // 检查冷却
        long now = System.currentTimeMillis();
        long cooldownTime = cooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (now < cooldownTime) {
            long timeLeft = (cooldownTime - now) / 1000;
            player.sendActionBar(
                    Component.text("技能冷却中... " + (timeLeft + 1) + "s", NamedTextColor.RED)
            );
            return;
        }

        // 获取玩家瞄准的方块 (最远100格)
        Block targetBlock = player.getTargetBlockExact(100);
        if (targetBlock == null || targetBlock.getType() == Material.AIR) {
            player.sendActionBar(Component.text("没有瞄准任何方块!", NamedTextColor.RED));
            return;
        }

        // --- ***修改在这里*** ---

        // 执行雷暴
        Location center = targetBlock.getLocation().add(0.5, 1, 0.5); // 方块顶部中心

        // Final 变量，供 Runnable 内部类使用
        final Location stormCenter = center;
        final World stormWorld = center.getWorld();
        final int radius = 5;
        // *** 修复: 把 'ticksPerRun' 移到这里 ***
        final int ticksPerRun = 4; // 每 4 ticks 运行一次

        new BukkitRunnable() {
            private int ticksElapsed = 0; // 已经过的 ticks
            private final int durationTicks = 60; // 持续时间 3 秒 * 20 ticks
            // private final int ticksPerRun = 4; // <-- 从这里移除
            private final int strikesPerRun = 2; // 每次运行召唤 2 道闪电

            @Override
            public void run() {
                if (ticksElapsed >= durationTicks) {
                    cancel(); // 3秒结束，停止任务
                    return;
                }

                // 每次运行召唤 2 道闪电
                for (int i = 0; i < strikesPerRun; i++) {
                    double x = stormCenter.getX() + (random.nextDouble() * 2 * radius) - radius; // -5 到 +5
                    double z = stormCenter.getZ() + (random.nextDouble() * 2 * radius) - radius;
                    Location strikeLoc = new Location(stormWorld, x, stormCenter.getY(), z);

                    // 确保闪电在方块表面
                    strikeLoc = stormWorld.getHighestBlockAt(strikeLoc).getLocation().add(0, 1, 0);
                    stormWorld.strikeLightning(strikeLoc); // 造成伤害
                }

                ticksElapsed += ticksPerRun; // 增加已过时间
            }
        }.runTaskTimer(plugin, 0L, ticksPerRun); // 立即开始, 每 4 ticks 重复

        // --- ***修改结束*** ---

        // 设置 30 秒冷却
        cooldowns.put(player.getUniqueId(), now + 30000); // 30 * 1000 ms
    }

    /**
     * 监听暴击：蜘蛛网与龙息 (此部分未修改)
     */
    @EventHandler
    public void onCrit(EntityDamageByEntityEvent event) {
        // 攻击者必须是玩家
        if (!(event.getDamager() instanceof Player damager)) {
            return;
        }
        // 受害者必须是玩家
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        // 必须是暴击
        if (!event.isCritical()) {
            return;
        }
        // 必须手持终末之剑
        if (!itemManager.isEndSword(damager.getInventory().getItemInMainHand())) {
            return;
        }

        // 更新暴击计数
        critCounts.putIfAbsent(damager.getUniqueId(), new HashMap<>());
        Map<UUID, Integer> targetCounts = critCounts.get(damager.getUniqueId());

        int newCount = targetCounts.getOrDefault(target.getUniqueId(), 0) + 1;

        if (newCount >= 5) {
            // 触发技能
            triggerCritAbility(target);
            // 重置计数
            targetCounts.remove(target.getUniqueId());
            damager.sendActionBar(Component.text("已对 " + target.getName() + " 触发暴击效果!", NamedTextColor.GREEN));
        } else {
            // 存储新计数
            targetCounts.put(target.getUniqueId(), newCount);
            // 提示
            damager.sendActionBar(
                    Component.text("暴击 " + target.getName() + " (" + newCount + "/5)", NamedTextColor.YELLOW)
            );
        }
    }

    /**
     * 触发5次暴击后的技能 (此部分未修改)
     * @param target 目标玩家
     */
    private void triggerCritAbility(Player target) {
        Location loc = target.getLocation();
        World world = target.getWorld();

        // 1. 在玩家脚下生成 2x2 蜘蛛网
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                loc.clone().add(x, 0, z).getBlock().setType(Material.COBWEB);
            }
        }

        // 2. 从 5 格高处发射龙息
        Location spawnLoc = loc.clone().add(0, 5, 0);
        DragonFireball fireball = world.spawn(spawnLoc, DragonFireball.class);

        // 让龙息朝下（朝向玩家）
        fireball.setDirection(new Vector(0, -1, 0));
        //
        fireball.setShooter(target); // 可以设置一个攻击者，这里设为 null 或 目标 避免奇怪的追踪
    }
}

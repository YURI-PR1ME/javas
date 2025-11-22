package com.example.tyrantpickaxe;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import org.bukkit.Color; // 导入 Color 类

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PickaxeListener implements Listener {

    private final TyrantPickaxe plugin;

    // ... (其他变量保持不变) ...
    private final Map<UUID, Long> teleportCooldown = new HashMap<>();
    private final Map<UUID, Long> teleportCritWindow = new HashMap<>();
    private final Map<UUID, Long> skillCooldown = new HashMap<>();
    private final Map<UUID, SkillSession> activeSkills = new HashMap<>();
    private final Map<UUID, Integer> killStreak = new HashMap<>();


    public PickaxeListener(TyrantPickaxe plugin) {
        this.plugin = plugin;
        startHasteCheckTask();
    }

    // --- 效果 1: 手持急迫 4 (保持不变) ---

    private void startHasteCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    ItemStack mainHand = player.getInventory().getItemInMainHand();
                    if (ItemManager.isTyrantPickaxe(mainHand, plugin)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 50, 3, true, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // --- 效果 2: 右键实体瞬移 ---

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (event.getHand() != EquipmentSlot.HAND || !ItemManager.isTyrantPickaxe(player.getInventory().getItemInMainHand(), plugin)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (teleportCooldown.getOrDefault(uuid, 0L) > now) {
            player.sendMessage(ChatColor.RED + "瞬移冷却中... (" + ((teleportCooldown.get(uuid) - now) / 1000.0) + "s)");
            return;
        }

        Entity target = event.getRightClicked();
        
        if (!(target instanceof LivingEntity)) {
            player.sendMessage(ChatColor.YELLOW + "你只能瞬移到生物背后！");
            return;
        }
        LivingEntity livingTarget = (LivingEntity) target;
        
        // 距离检查 (40格)
        if (player.getLocation().distance(livingTarget.getLocation()) > 40) {
            player.sendMessage(ChatColor.YELLOW + "目标太远了！ (最大距离 40 格)");
            return;
        }
        
        event.setCancelled(true);

        // --- 传送逻辑 (保持不变) ---
        Location targetLoc = livingTarget.getLocation();
        Vector direction = targetLoc.getDirection().setY(0).normalize();
        Location teleportTo = targetLoc.clone().subtract(direction.multiply(1)).add(0, 1, 0);

        if (teleportTo.getBlock().getType().isSolid()) {
            teleportTo.add(0, 1, 0);
        }
        
        Location playerHeadAtDest = teleportTo.clone().add(0, player.getEyeHeight(), 0);
        Vector lookDirection = livingTarget.getEyeLocation().toVector().subtract(playerHeadAtDest.toVector());
        teleportTo.setDirection(lookDirection);

        playTeleportEffect(player.getLocation(), teleportTo);
        
        player.teleport(teleportTo);

        teleportCooldown.put(uuid, now + 3000L);
        teleportCritWindow.put(uuid, now + 2000L);
    }

    // --- 效果 2 (续): 暴击重置冷却 (保持不变) ---
    
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getDamager();
        UUID uuid = player.getUniqueId();

        if (teleportCritWindow.getOrDefault(uuid, 0L) > System.currentTimeMillis()) {
            if (event.isCritical() && ItemManager.isTyrantPickaxe(player.getInventory().getItemInMainHand(), plugin)) {
                teleportCooldown.remove(uuid);
                teleportCritWindow.remove(uuid);
                player.sendMessage(ChatColor.GREEN + "瞬移冷却已重置！");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            }
        }
        
        if (activeSkills.containsKey(uuid)) {
            SkillSession session = activeSkills.get(uuid);
            if (event.getEntity() == session.getCurrentTarget()) {
                if (ItemManager.isTyrantPickaxe(player.getInventory().getItemInMainHand(), plugin)) {
                    session.registerHit();
                    if (session.attackWindowTask != null) {
                        session.attackWindowTask.cancel();
                    }
                    advanceSkill(player);
                }
            }
        }
    }


    // --- 效果 3: 快速位移 (下蹲+右键) (保持不变) ---

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!player.isSneaking() || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!ItemManager.isTyrantPickaxe(player.getInventory().getItemInMainHand(), plugin)) {
            return;
        }

        event.setCancelled(true);

        if (activeSkills.containsKey(uuid)) {
            player.sendMessage(ChatColor.YELLOW + "技能已在激活状态！");
            return;
        }

        long now = System.currentTimeMillis();
        if (skillCooldown.getOrDefault(uuid, 0L) > now) {
            player.sendMessage(ChatColor.RED + "快速位移冷却中... (" + ((skillCooldown.get(uuid) - now) / 1000.0) + "s)");
            return;
        }

        List<Player> nearbyPlayers = player.getNearbyEntities(10, 10, 10).stream()
                .filter(e -> e instanceof Player && e != player)
                .map(e -> (Player) e)
                .collect(Collectors.toList());

        if (nearbyPlayers.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "附近 10 格内没有玩家！");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "快速位移... 启动！");
        SkillSession session = new SkillSession(nearbyPlayers);
        activeSkills.put(uuid, session);
        skillCooldown.put(uuid, now + 60000L); 
        advanceSkill(player);
    }

    /**
     * 技能逻辑：传送到下一个目标 (保持不变)
     */
    private void advanceSkill(Player player) {
        SkillSession session = activeSkills.get(player.getUniqueId());
        if (session == null) return;

        if (session.isComplete()) {
            endSkill(player, false); 
            return;
        }

        Player target = session.getCurrentTarget();
        
        if (target == null || !target.isOnline() || target.isDead() || target.getLocation().distance(player.getLocation()) > 20) {
            session.advance();
            advanceSkill(player); 
            return;
        }
        
        Location targetLoc = target.getLocation();
        Vector direction = targetLoc.getDirection().setY(0).normalize();
        Location teleportTo = targetLoc.clone().subtract(direction.multiply(1)).add(0, 1, 0);
        
        if (teleportTo.getBlock().getType().isSolid()) {
            teleportTo.add(0, 1, 0);
        }

        Location playerHeadAtDest = teleportTo.clone().add(0, player.getEyeHeight(), 0);
        Vector lookDirection = target.getEyeLocation().toVector().subtract(playerHeadAtDest.toVector());
        teleportTo.setDirection(lookDirection);

        playSkillTeleportEffect(player.getLocation(), teleportTo);
        
        player.teleport(teleportTo);
        player.setAllowFlight(true);
        player.setFlying(true);
        
        session.attackWindowTask = new BukkitRunnable() {
            @Override
            public void run() {
                player.sendMessage(ChatColor.RED + "未能在 2 秒内攻击... 技能中断。");
                endSkill(player, true); 
            }
        }.runTaskLater(plugin, 40L);
    }

    /**
     * 结束技能 (保持不变)
     */
    private void endSkill(Player player, boolean failed) {
        SkillSession session = activeSkills.remove(player.getUniqueId());
        if (session == null) return;

        if (session.attackWindowTask != null && !session.attackWindowTask.isCancelled()) {
            session.attackWindowTask.cancel();
        }

        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(false);
        }
        player.setFlying(false); 

        long dynamicCdSeconds = (session.getTotalHits() * 5L) + 10L;
        long newCooldownMillis = dynamicCdSeconds * 1000L;
        skillCooldown.put(player.getUniqueId(), System.currentTimeMillis() + newCooldownMillis);

        player.sendMessage(ChatColor.GOLD + "技能结束！总攻击次数: " + session.getTotalHits() + "。 冷却时间: " + dynamicCdSeconds + "秒。");
    }


    // --- 效果 4: 连杀 (保持不变) ---

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) {
            return;
        }
        
        UUID killerUuid = killer.getUniqueId();

        if (!ItemManager.isTyrantPickaxe(killer.getInventory().getItemInMainHand(), plugin)) {
            killStreak.remove(killerUuid);
            return;
        }

        int currentStreak = killStreak.getOrDefault(killerUuid, 0) + 1;
        killStreak.put(killerUuid, currentStreak);
        killer.sendMessage(ChatColor.GRAY + "当前连杀: " + currentStreak);

        if (currentStreak >= 5) {
            killStreak.remove(killerUuid);

            killer.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Now, who is the Tyrant? HA HA HA!");
            killer.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
            killer.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0));
            killer.playSound(killer.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.5f);
        }
    }


    // --- 辅助方法和内部类 ---

    /**
     * [已修复] 播放瞬移粒子 (效果 2) - 改为红色
     */
    private void playTeleportEffect(Location from, Location to) {
        // 定义红色粒子
        Particle.DustOptions redDust = new Particle.DustOptions(Color.RED, 1.0F);
        
        // 原地粒子 (修复: REDSTONE -> DUST)
        from.getWorld().spawnParticle(Particle.DUST, from.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5, redDust);
        from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        
        // 目标地粒子 (修复: REDSTONE -> DUST)
        to.getWorld().spawnParticle(Particle.DUST, to.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5, redDust);
        to.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
    }
    
    /**
     * [已修复] 播放技能瞬移粒子 (效果 3) - 改为红色
     */
    private void playSkillTeleportEffect(Location from, Location to) {
        // 定义红色粒子 (大)
        Particle.DustOptions redDust = new Particle.DustOptions(Color.MAROON, 1.5F); // 暗红色，大颗粒
        
        // 原地粒子 (使用 "伤害指示器" 粒子，是红色的叉)
        from.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, from.clone().add(0, 1, 0), 30, 0.3, 0.3, 0.3, 0.05);
        from.getWorld().playSound(from, Sound.ENTITY_GHAST_SHOOT, 0.8f, 1.5f);
        
        // 目标地粒子 (修复: REDSTONE -> DUST)
        to.getWorld().spawnParticle(Particle.DUST, to.clone().add(0, 1, 0), 40, 0.5, 0.5, 0.5, redDust);
    }

    /**
     * 内部类 (保持不变)
     */
    private static class SkillSession {
        private final List<Player> targetQueue;
        private int currentTargetIndex;
        private int totalHits;
        public BukkitTask attackWindowTask; 

        public SkillSession(List<Player> initialTargets) {
            this.targetQueue = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                this.targetQueue.addAll(initialTargets);
            }
            this.currentTargetIndex = 0;
            this.totalHits = 0;
        }

        public Player getCurrentTarget() {
            if (isComplete()) return null;
            return targetQueue.get(currentTargetIndex);
        }

        public void advance() {
            currentTargetIndex++;
        }
        
        public void registerHit() {
            totalHits++;
        }

        public int getTotalHits() {
            return totalHits;
        }

        public boolean isComplete() {
            return currentTargetIndex >= targetQueue.size();
        }
    }
}

package com.yourname.arenaplugin;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import com.yourname.creditplugin.CreditManager;
import com.yourname.creditplugin.CreditPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaManager {
    
    private final Map<String, Arena> arenas = new HashMap<>();
    private final Map<UUID, Long> playerSelectionTime = new ConcurrentHashMap<>();
    private final Map<UUID, Location> playerLastLocations = new ConcurrentHashMap<>();
    private final Set<UUID> playersInArena = ConcurrentHashMap.newKeySet(); // 跟踪在擂台内的玩家
    private Arena currentArena;
    private BukkitRunnable preparationTask; // 准备阶段的任务
    
    public ArenaManager() {
        loadArenas();
    }
    
    // 创建擂台区域
    public boolean createArena(String name, Location pos1, Location pos2) {
        FileConfiguration config = ArenaPlugin.getInstance().getConfig();
        int minSize = config.getInt("arena.min-size", 10);
        
        // 检查最小尺寸
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        
        if (maxX - minX < minSize || maxZ - minZ < minSize) {
            Bukkit.getLogger().warning("擂台创建失败: 尺寸太小 " + (maxX - minX) + "x" + (maxZ - minZ) + "，需要至少 " + minSize + "x" + minSize);
            return false;
        }
        
        // 确保两个点在同一个世界
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            Bukkit.getLogger().warning("擂台创建失败: 两个点不在同一个世界");
            return false;
        }
        
        Arena arena = new Arena(name, pos1, pos2);
        arenas.put(name, arena);
        saveArenas();
        
        // 广播擂台创建消息
        Bukkit.broadcastMessage(ChatColor.GREEN + "🏟️ 擂台 '" + name + "' 已创建！");
        Bukkit.broadcastMessage(ChatColor.GRAY + "范围: " + arena.getBoundsInfo());
        return true;
    }
    
    // 检查玩家边界和状态
    public void checkBoundaries() {
        if (currentArena == null) {
            // 调试信息：没有激活的擂台
            if (System.currentTimeMillis() % 10000 < 50) { // 每10秒输出一次
                Bukkit.getLogger().info("擂台系统: 当前没有激活的擂台");
            }
            return;
        }
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean currentlyInArena = currentArena.isInArena(player);
            boolean wasInArena = playersInArena.contains(player.getUniqueId());
            
            // 玩家进入擂台
            if (currentlyInArena && !wasInArena) {
                handlePlayerEnterArena(player);
            }
            // 玩家离开擂台
            else if (!currentlyInArena && wasInArena) {
                handlePlayerLeaveArena(player);
            }
            
            // 如果玩家在擂台内，检查边界和比赛状态
            if (currentlyInArena) {
                // 检查是否接近边界
                if (currentArena.isNearBoundary(player)) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                        ArenaPlugin.getInstance().getConfig().getString("messages.boundary-warning", "&c⚠ 警告！你接近擂台边界！")));
                    
                    // 显示边界粒子效果
                    showBoundaryParticles(player);
                }
            }
            
            // 第三阶段：比赛进行中，选手出界直接判负
            if (currentArena.getState() == ArenaState.IN_PROGRESS && 
                currentArena.isFighter(player) && !currentArena.isInArena(player)) {
                handlePlayerExit(player);
            }
            
            // 第二阶段：准备阶段，选手离开直接判负
            if (currentArena.getState() == ArenaState.PREPARATION && 
                currentArena.isFighter(player) && !currentArena.isInArena(player)) {
                handlePlayerEscapeInPreparation(player);
            }
        }
    }
    
    // 处理玩家进入擂台
    private void handlePlayerEnterArena(Player player) {
        playersInArena.add(player.getUniqueId());
        
        Bukkit.getLogger().info("玩家 " + player.getName() + " 进入擂台区域");
        
        if (currentArena.getState() == ArenaState.WAITING_FOR_PLAYERS) {
            player.sendMessage(ChatColor.YELLOW + "🏟️ 你已进入擂台区域！");
            player.sendMessage(ChatColor.GREEN + "💡 提示：在擂台内停留10秒可成为选手");
            
            // 显示进入特效
            showEnterEffect(player);
        } else if (currentArena.getState() == ArenaState.IN_PROGRESS) {
            player.sendMessage(ChatColor.YELLOW + "👀 你正在观战擂台比赛！");
        } else if (currentArena.getState() == ArenaState.PREPARATION) {
            player.sendMessage(ChatColor.YELLOW + "⏰ 比赛准备中，选手已确定！");
        }
    }
    
    // 处理玩家离开擂台
    private void handlePlayerLeaveArena(Player player) {
        playersInArena.remove(player.getUniqueId());
        
        Bukkit.getLogger().info("玩家 " + player.getName() + " 离开擂台区域");
        
        // 第一阶段：选手选择阶段可以自由离开
        if (currentArena.getState() == ArenaState.WAITING_FOR_PLAYERS) {
            // 如果玩家是正在等待的候选选手，重置他们的计时
            if (playerSelectionTime.containsKey(player.getUniqueId())) {
                playerSelectionTime.remove(player.getUniqueId());
                playerLastLocations.remove(player.getUniqueId());
                player.sendMessage(ChatColor.RED + "❌ 你已离开擂台区域，选手资格被取消");
            }
        }
        // 第二阶段和第三阶段：选手离开已经在checkBoundaries中处理
    }
    
    // 处理准备阶段选手逃跑
    private void handlePlayerEscapeInPreparation(Player escapee) {
        Player winner = currentArena.getOpponent(escapee);
        if (winner != null) {
            Bukkit.getLogger().info("选手 " + escapee.getName() + " 在准备阶段离开擂台，判负");
            
            // 取消准备阶段的任务
            if (preparationTask != null && !preparationTask.isCancelled()) {
                preparationTask.cancel();
            }
            
            // 直接结束比赛，获胜者获得奖励
            endMatch(winner, escapee, "准备阶段逃跑", false);
            
            // 广播逃跑消息
            String escapeMessage = ChatColor.translateAlternateColorCodes('&',
                String.format(ArenaPlugin.getInstance().getConfig().getString("messages.preparation-escape", 
                    "&c%s 在准备阶段逃跑！%s 自动获胜！"), escapee.getName(), winner.getName()));
            Bukkit.broadcastMessage(escapeMessage);
        }
    }
    
    // 显示进入特效
    private void showEnterEffect(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        
        // 绿色粒子效果表示进入
        for (int i = 0; i < 20; i++) {
            double offsetX = (Math.random() - 0.5) * 3;
            double offsetZ = (Math.random() - 0.5) * 3;
            Location particleLoc = loc.clone().add(offsetX, 1, offsetZ);
            world.spawnParticle(Particle.VILLAGER_HAPPY, particleLoc, 1);
        }
    }
    
    // 检查选手选择
    public void checkPlayerSelection() {
        if (currentArena == null || currentArena.getState() != ArenaState.WAITING_FOR_PLAYERS) return;
        
        long currentTime = System.currentTimeMillis();
        List<Player> candidates = new ArrayList<>();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (currentArena.isInArena(player) && player.getGameMode() == GameMode.SURVIVAL) {
                UUID playerId = player.getUniqueId();
                
                if (!playerSelectionTime.containsKey(playerId)) {
                    playerSelectionTime.put(playerId, currentTime);
                    playerLastLocations.put(playerId, player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "⏱️ 选手资格计时开始！请在擂台内停留10秒");
                    
                    Bukkit.getLogger().info("开始为玩家 " + player.getName() + " 计时");
                } else {
                    // 检查玩家是否移动（防止挂机）
                    Location lastLoc = playerLastLocations.get(playerId);
                    Location currentLoc = player.getLocation();
                    
                    if (lastLoc.distanceSquared(currentLoc) > 1) {
                        playerSelectionTime.put(playerId, currentTime);
                        playerLastLocations.put(playerId, currentLoc);
                    }
                    
                    long timeInArena = currentTime - playerSelectionTime.get(playerId);
                    long remainingTime = 10000 - timeInArena;
                    
                    // 每2秒提醒一次剩余时间
                    if (remainingTime > 0 && remainingTime % 2000 < 50) {
                        player.sendMessage(ChatColor.YELLOW + "⏰ 还需停留 " + (remainingTime / 1000) + " 秒");
                    }
                    
                    if (timeInArena >= 10000) { // 10秒
                        candidates.add(player);
                        player.sendMessage(ChatColor.GREEN + "✅ 你已获得选手资格！");
                        Bukkit.getLogger().info("玩家 " + player.getName() + " 获得选手资格");
                    }
                }
            } else {
                // 玩家离开擂台或不是生存模式，移除计时
                if (playerSelectionTime.containsKey(player.getUniqueId())) {
                    playerSelectionTime.remove(player.getUniqueId());
                    playerLastLocations.remove(player.getUniqueId());
                }
            }
        }
        
        // 选择前两名玩家
        if (candidates.size() >= 2) {
            Player player1 = candidates.get(0);
            Player player2 = candidates.get(1);
            
            Bukkit.getLogger().info("选择选手: " + player1.getName() + " 和 " + player2.getName());
            
            // 清除所有玩家的计时
            playerSelectionTime.clear();
            playerLastLocations.clear();
            
            startMatchPreparation(player1, player2);
        }
    }
    
    // 开始比赛准备
    private void startMatchPreparation(Player player1, Player player2) {
        currentArena.setFighters(player1, player2);
        currentArena.setState(ArenaState.PREPARATION);
        
        // 广播消息
        String message = ChatColor.translateAlternateColorCodes('&',
            String.format(ArenaPlugin.getInstance().getConfig().getString("messages.match-starting", 
                "&a比赛将在 %d 秒后开始！红队: %s vs 蓝队: %s"), 
                ArenaPlugin.getInstance().getConfig().getInt("arena.preparation-time"), 
                player1.getName(), player2.getName()));
        Bukkit.broadcastMessage(message);
        
        // 给选手发送警告消息
        currentArena.broadcastToFighters("&c⚠ 警告：准备阶段离开擂台将直接判负！");
        currentArena.broadcastToFighters("&6💰 奖金规则:");
        currentArena.broadcastToFighters("&6- 比赛开始后发放35点信用点");
        currentArena.broadcastToFighters("&6- 击败对手：保留全部35点");
        currentArena.broadcastToFighters("&6- 对手下注完毕逃跑：扣除45点，获胜者获得10点");
        currentArena.broadcastToFighters("&6- 自己死亡：扣除35点");
        
        // 开放下注
        ArenaPlugin.getInstance().getBetManager().openBetting(currentArena);
        
        // 开始倒计时
        preparationTask = new BukkitRunnable() {
            int countdown = ArenaPlugin.getInstance().getConfig().getInt("arena.preparation-time");
            
            @Override
            public void run() {
                // 检查选手是否还在擂台内
                if (!currentArena.areBothFightersInArena()) {
                    // 如果有选手不在擂台内，已经在checkBoundaries中处理了
                    cancel();
                    return;
                }
                
                if (countdown <= 0) {
                    startMatch();
                    cancel();
                } else if (countdown <= 5 || countdown % 10 == 0) {
                    // 最后5秒和每10秒提醒
                    String countdownMsg = ChatColor.YELLOW + "⏰ 比赛将在 " + countdown + " 秒后开始！";
                    Bukkit.broadcastMessage(countdownMsg);
                    
                    // 给选手发送特殊提醒
                    currentArena.broadcastToFighters("&e⏱️ 准备时间剩余: " + countdown + "秒");
                }
                countdown--;
            }
        };
        preparationTask.runTaskTimer(ArenaPlugin.getInstance(), 0L, 20L); // 每秒执行
    }
    
    // 开始比赛
    private void startMatch() {
        currentArena.setState(ArenaState.IN_PROGRESS);
        
        // 给选手发送消息
        currentArena.broadcastToFighters("&a🎉 比赛开始！");
        currentArena.broadcastToFighters("&c⚔️ 战斗吧！将对手击败或推出擂台！");
        currentArena.broadcastToFighters("&c⚠ 注意：比赛开始后离开擂台将直接判负！");
        
        // 广播比赛开始
        Bukkit.broadcastMessage(ChatColor.GOLD + "🎉 擂台比赛正式开始！");
        
        // 显示开始特效
        showStartEffect();
        
        // 关闭下注
        ArenaPlugin.getInstance().getBetManager().closeBetting();
        
        Bukkit.getLogger().info("比赛开始: " + currentArena.getRedPlayer().getName() + " vs " + currentArena.getBluePlayer().getName());
        
        // 先给两位选手发放35点信用点
        CreditManager creditManager = getCreditManager();
        if (creditManager != null) {
            if (currentArena.getRedPlayer() != null) {
                creditManager.addCredits(currentArena.getRedPlayer(), 35);
                currentArena.getRedPlayer().sendMessage(ChatColor.GREEN + "💰 你获得了35点信用点预付款！");
            }
            if (currentArena.getBluePlayer() != null) {
                creditManager.addCredits(currentArena.getBluePlayer(), 35);
                currentArena.getBluePlayer().sendMessage(ChatColor.GREEN + "💰 你获得了35点信用点预付款！");
            }
        }
    }
    
    // 显示开始特效
    private void showStartEffect() {
        if (currentArena == null) return;
        
        Location center = currentArena.getCenter();
        World world = center.getWorld();
        
        // 使用兼容的粒子效果
        for (int i = 0; i < 10; i++) {
            double offsetX = (Math.random() - 0.5) * 10;
            double offsetZ = (Math.random() - 0.5) * 10;
            Location effectLoc = center.clone().add(offsetX, 2, offsetZ);
            
            // 使用火焰粒子效果
            world.spawnParticle(Particle.FLAME, effectLoc, 5);
            // 添加一些烟雾效果
            world.spawnParticle(Particle.SMOKE_LARGE, effectLoc, 3);
        }
        
        // 给选手添加红蓝发光粒子效果
        if (currentArena.getRedPlayer() != null) {
            currentArena.getRedPlayer().sendMessage(ChatColor.RED + "✨ 你身上散发着红光！");
            showPlayerColorEffect(currentArena.getRedPlayer(), Color.RED);
        }
        if (currentArena.getBluePlayer() != null) {
            currentArena.getBluePlayer().sendMessage(ChatColor.BLUE + "✨ 你身上散发着蓝光！");
            showPlayerColorEffect(currentArena.getBluePlayer(), Color.BLUE);
        }
    }
    
    // 显示选手颜色特效
    private void showPlayerColorEffect(Player player, Color color) {
        if (player == null) return;
        
        Location loc = player.getLocation();
        World world = loc.getWorld();
        
        // 在玩家周围生成彩色粒子
        for (int i = 0; i < 20; i++) {
            double offsetX = (Math.random() - 0.5) * 2;
            double offsetY = (Math.random() - 0.5) * 2 + 1; // 在玩家腰部高度
            double offsetZ = (Math.random() - 0.5) * 2;
            Location particleLoc = loc.clone().add(offsetX, offsetY, offsetZ);
            
            // 使用彩色粒子效果
            world.spawnParticle(Particle.REDSTONE, particleLoc, 1, 
                new Particle.DustOptions(color, 1.5f));
        }
        
        // 持续显示粒子效果（比赛期间）
        new BukkitRunnable() {
            int count = 0;
            final int maxCount = 200; // 持续10秒 (200 ticks)
            
            @Override
            public void run() {
                // 检查比赛是否结束或玩家离线
                if (currentArena == null || 
                    currentArena.getState() != ArenaState.IN_PROGRESS || 
                    !player.isOnline() ||
                    count >= maxCount) {
                    cancel();
                    return;
                }
                
                // 每5 tick显示一次粒子效果
                if (count % 5 == 0) {
                    Location currentLoc = player.getLocation();
                    for (int i = 0; i < 5; i++) {
                        double offsetX = (Math.random() - 0.5) * 1.5;
                        double offsetY = (Math.random() - 0.5) * 1.5 + 1;
                        double offsetZ = (Math.random() - 0.5) * 1.5;
                        Location particleLoc = currentLoc.clone().add(offsetX, offsetY, offsetZ);
                        
                        world.spawnParticle(Particle.REDSTONE, particleLoc, 1, 
                            new Particle.DustOptions(color, 1.2f));
                    }
                }
                
                count++;
            }
        }.runTaskTimer(ArenaPlugin.getInstance(), 0L, 1L);
    }
    
    // 处理玩家离开擂台（比赛进行中）
    private void handlePlayerExit(Player player) {
        if (currentArena.isFighter(player)) {
            Player winner = currentArena.getOpponent(player);
            if (winner != null) {
                Bukkit.getLogger().info("选手 " + player.getName() + " 在比赛进行中离开擂台，判负");
                endMatch(winner, player, "下注完毕逃跑", false); // 对手逃跑，按新规则处理
            }
        }
    }
    
    // 结束比赛
    public void endMatch(Player winner, Player loser, String reason, boolean isKill) {
        if (currentArena == null) return;
        
        // 保存比赛状态，因为后面会设置为FINISHED
        boolean wasInProgress = (currentArena.getState() == ArenaState.IN_PROGRESS);
        
        currentArena.setState(ArenaState.FINISHED);
        
        // 取消准备阶段的任务（如果还在运行）
        if (preparationTask != null && !preparationTask.isCancelled()) {
            preparationTask.cancel();
        }
        
        // 广播结果
        String resultMessage = ChatColor.translateAlternateColorCodes('&',
            String.format(ArenaPlugin.getInstance().getConfig().getString("messages.match-result", 
                "&6🎉 比赛结果：%s 获胜！原因：%s"), winner.getName(), reason));
        Bukkit.broadcastMessage(resultMessage);
        
        // 给选手发送结果
        winner.sendMessage(ChatColor.GOLD + "🏆 恭喜你获得胜利！");
        if (loser != null) {
            loser.sendMessage(ChatColor.RED + "💔 很遗憾，你输了比赛");
        }
        
        // 处理奖金（只有在比赛已经开始的情况下）
        if (wasInProgress) {
            CreditManager creditManager = getCreditManager();
            if (creditManager != null) {
                if (isKill) {
                    // 直接杀死对手，保留全部35点
                    winner.sendMessage(ChatColor.GOLD + "💰 你击败了对手，保留了全部35点信用点！");
                    Bukkit.getLogger().info(winner.getName() + " 击败对手，保留35点信用点");
                } else {
                    // 对手下注完毕逃跑，按新规则处理
                    if (reason.contains("逃跑")) {
                        // 获胜者获得10点奖励
                        creditManager.addCredits(winner, 10);
                        winner.sendMessage(ChatColor.GOLD + "💰 对手" + reason + "，你获得了10点奖励！");
                        
                        // 失败者扣除45点
                        if (creditManager.removeCredits(loser, 45)) {
                            loser.sendMessage(ChatColor.RED + "💸 你因" + reason + "被扣除45点信用点！");
                        } else {
                            // 如果信用点不足45，只扣除能扣除的部分
                            int currentCredits = creditManager.getCredits(loser);
                            creditManager.setCredits(loser, 0);
                            loser.sendMessage(ChatColor.RED + "💸 你因" + reason + "被扣除" + currentCredits + "点信用点！");
                        }
                        Bukkit.getLogger().info(winner.getName() + " 对手" + reason + "，获得10点奖励，失败者扣除45点");
                    } else {
                        // 其他非击杀情况（如出界等），保持原规则
                        if (creditManager.removeCredits(winner, 15)) {
                            winner.sendMessage(ChatColor.GOLD + "💰 对手" + reason + "，你保留了20点信用点（扣除15点）！");
                        } else {
                            // 如果信用点不足15，只扣除能扣除的部分
                            int currentCredits = creditManager.getCredits(winner);
                            creditManager.setCredits(winner, 0);
                            winner.sendMessage(ChatColor.GOLD + "💰 对手" + reason + "，你保留了" + (35 - currentCredits) + "点信用点！");
                        }
                        Bukkit.getLogger().info(winner.getName() + " 对手" + reason + "，保留20点信用点");
                    }
                }
                
                // 失败者扣除35点（只在被杀死的情况下）
                if (loser != null && isKill) {
                    if (creditManager.removeCredits(loser, 35)) {
                        loser.sendMessage(ChatColor.RED + "💸 你因比赛失败被扣除35点信用点！");
                    } else {
                        // 如果信用点不足35，只扣除能扣除的部分
                        int currentCredits = creditManager.getCredits(loser);
                        creditManager.setCredits(loser, 0);
                        loser.sendMessage(ChatColor.RED + "💸 你因比赛失败被扣除" + currentCredits + "点信用点！");
                    }
                }
            } else {
                Bukkit.getLogger().warning("无法获取信用点管理器，无法处理奖金");
            }
        } else {
            // 准备阶段逃跑，不需要处理信用点（因为还没有发放）
            Bukkit.getLogger().info("准备阶段逃跑，不处理信用点");
        }
        
        // 通知选手现在可以下注了
        if (currentArena.getRedPlayer() != null) {
            currentArena.getRedPlayer().sendMessage(ChatColor.GREEN + "💰 比赛结束，你现在可以下注其他比赛了！");
        }
        if (currentArena.getBluePlayer() != null) {
            currentArena.getBluePlayer().sendMessage(ChatColor.GREEN + "💰 比赛结束，你现在可以下注其他比赛了！");
        }
        
        // 结算下注
        ArenaPlugin.getInstance().getBetManager().settleBets(currentArena, winner);
        
        // 重置计时器
        playerSelectionTime.clear();
        playerLastLocations.clear();
        playersInArena.clear();
        
        Bukkit.getLogger().info("比赛结束: " + winner.getName() + " 获胜，原因: " + reason);
        
        // 5秒后重置擂台
        Bukkit.getScheduler().runTaskLater(ArenaPlugin.getInstance(), () -> {
            if (currentArena != null) {
                currentArena.reset();
                currentArena.setState(ArenaState.WAITING_FOR_PLAYERS);
                Bukkit.broadcastMessage(ChatColor.YELLOW + "🔄 擂台已重置，等待新的选手！");
            }
        }, 100L);
    }
    
    // 显示边界粒子效果
    private void showBoundaryParticles(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        
        for (int i = 0; i < 10; i++) {
            double offsetX = (Math.random() - 0.5) * 2;
            double offsetZ = (Math.random() - 0.5) * 2;
            Location particleLoc = loc.clone().add(offsetX, 0.5, offsetZ);
            world.spawnParticle(Particle.REDSTONE, particleLoc, 1, 
                new Particle.DustOptions(Color.RED, 1));
        }
    }
    
    // 保存和加载擂台数据
    private void saveArenas() {
        // 这里可以添加保存擂台数据到文件的逻辑
        // 暂时只保存在内存中
        Bukkit.getLogger().info("擂台 '" + arenas.keySet() + "' 已保存到内存");
    }
    
    private void loadArenas() {
        // 这里可以添加从文件加载擂台数据的逻辑
        // 暂时只加载内存中的数据
        Bukkit.getLogger().info("擂台数据加载完成");
    }
    
    // 获取信用点管理器
    private CreditManager getCreditManager() {
        try {
            return CreditPlugin.getInstance().getCreditManager();
        } catch (Exception e) {
            Bukkit.getLogger().severe("无法获取信用点管理器: " + e.getMessage());
            return null;
        }
    }
    
    // Getter 方法
    public Arena getCurrentArena() {
        return currentArena;
    }
    
    public void setCurrentArena(Arena arena) {
        this.currentArena = arena;
        if (arena != null) {
            arena.setState(ArenaState.WAITING_FOR_PLAYERS);
            // 清除之前的玩家状态
            playersInArena.clear();
            playerSelectionTime.clear();
            playerLastLocations.clear();
            
            // 取消可能存在的准备任务
            if (preparationTask != null && !preparationTask.isCancelled()) {
                preparationTask.cancel();
            }
            
            Bukkit.getLogger().info("激活擂台: " + arena.getName() + " - " + arena.getBoundsInfo());
            Bukkit.broadcastMessage(ChatColor.GREEN + "🏟️ 擂台 '" + arena.getName() + "' 已激活！");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "📍 范围: " + arena.getBoundsInfo());
        }
    }
    
    public Map<String, Arena> getArenas() {
        return arenas;
    }
}

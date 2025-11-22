package com.yourname.playerhireplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.FileConfiguration;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HireManager {
    
// 在 HireManager 类中添加以下方法

/**
 * 发送匿名消息给会话的另一方
 */
public boolean sendMessage(UUID sessionId, Player sender, String message) {
    HireSession session = activeSessions.get(sessionId);
    if (session == null) {
        sender.sendMessage(ChatColor.RED + "❌ 会话不存在或已过期");
        return false;
    }
    
    // 验证发送者是否是会话的参与者
    boolean isBuyer = session.getBuyerId().equals(sender.getUniqueId());
    boolean isAssassin = session.getAssassinId().equals(sender.getUniqueId());
    
    if (!isBuyer && !isAssassin) {
        sender.sendMessage(ChatColor.RED + "❌ 你不是该会话的参与者");
        return false;
    }
    
    // 确定接收者
    UUID receiverId = isBuyer ? session.getAssassinId() : session.getBuyerId();
    Player receiver = Bukkit.getPlayer(receiverId);
    
    if (receiver == null || !receiver.isOnline()) {
        sender.sendMessage(ChatColor.RED + "❌ 对方不在线，无法发送消息");
        return false;
    }
    
    // 确定角色标识（匿名）
    String senderRole = isBuyer ? "雇主" : "刺客";
    String receiverRole = isBuyer ? "刺客" : "雇主";
    
    // 构建匿名消息
    String anonymousMessage = ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "密电" + ChatColor.DARK_GRAY + "] " + 
                             ChatColor.GRAY + senderRole + ChatColor.DARK_GRAY + " → " + 
                             ChatColor.GRAY + receiverRole + ChatColor.DARK_GRAY + ": " + 
                             ChatColor.WHITE + message;
    
    // 发送给接收者
    receiver.sendMessage(anonymousMessage);
    
    // 发送确认给发送者
    String confirmationMessage = ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "密电" + ChatColor.DARK_GRAY + "] " + 
                                ChatColor.GRAY + "你" + ChatColor.DARK_GRAY + " → " + 
                                ChatColor.GRAY + receiverRole + ChatColor.DARK_GRAY + ": " + 
                                ChatColor.WHITE + message;
    sender.sendMessage(confirmationMessage);
    
    // 记录日志（可选）
    PlayerHirePlugin.getInstance().getLogger().info(
        "密电消息 - 会话: " + sessionId.toString().substring(0, 8) + 
        ", 发送者: " + sender.getName() + "(" + senderRole + ")" +
        ", 接收者: " + receiver.getName() + "(" + receiverRole + ")" +
        ", 消息: " + message
    );
    
    return true;
}

    // 刺客档案
    private final Map<UUID, AssassinProfile> assassinProfiles = new ConcurrentHashMap<>();
    // 活跃合约
    private final Map<UUID, HireContract> activeContracts = new ConcurrentHashMap<>();
    // 雇佣会话
    private final Map<UUID, HireSession> activeSessions = new ConcurrentHashMap<>();
    // 玩家冷却时间
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    
    // 配置
    private int registrationFee;
    private long contractTimeout;
    private long cooldownTime;
    
    public HireManager() {
        loadConfig();
        loadAllData();
    }
    
    private void loadConfig() {
        FileConfiguration config = PlayerHirePlugin.getInstance().getConfig();
        
        // 设置默认值
        config.addDefault("registration-fee", 10);
        config.addDefault("contract-timeout", 86400000); // 24小时
        config.addDefault("cooldown-time", 300000); // 5分钟
        
        config.options().copyDefaults(true);
        PlayerHirePlugin.getInstance().saveConfig();
        
        registrationFee = config.getInt("registration-fee");
        contractTimeout = config.getLong("contract-timeout");
        cooldownTime = config.getLong("cooldown-time");
    }
    
    public void reloadConfig() {
        PlayerHirePlugin.getInstance().reloadConfig();
        loadConfig();
    }
    
    // 注册成为刺客
    public boolean registerAsAssassin(Player player) {
        UUID playerId = player.getUniqueId();
        
        // 检查是否已经注册
        if (assassinProfiles.containsKey(playerId)) {
            player.sendMessage(ChatColor.RED + "❌ 你已经注册成为刺客了！");
            return false;
        }
        
        // 检查资格核验：需要2倍入场资金的信用点
        int requiredCredits = registrationFee * 2;
        int playerCredits = getPlayerCredits(player);
        
        if (playerCredits < requiredCredits) {
            player.sendMessage(ChatColor.RED + "❌ 资格核验失败！需要至少 " + requiredCredits + " 点信用点");
            return false;
        }
        
        // 扣除入场资金
        if (!removeCredits(player, registrationFee)) {
            player.sendMessage(ChatColor.RED + "❌ 信用点扣除失败！");
            return false;
        }
        
        // 创建刺客档案
        AssassinProfile profile = new AssassinProfile(
            playerId,
            player.getName(),
            System.currentTimeMillis()
        );
        
        assassinProfiles.put(playerId, profile);
        player.sendMessage(ChatColor.GREEN + "✅ 成功注册成为刺客！入场费 " + registrationFee + " 点信用点已扣除");
        
        saveAssassinProfile(profile);
        return true;
    }
    
    // 获取可用刺客列表（匿名）
    public List<AssassinProfile> getAvailableAssassins() {
        List<AssassinProfile> available = new ArrayList<>();
        
        for (AssassinProfile profile : assassinProfiles.values()) {
            Player assassin = Bukkit.getPlayer(profile.getPlayerId());
            if (assassin != null && assassin.isOnline() && 
                assassin.getGameMode() != GameMode.SPECTATOR) {
                available.add(profile);
            }
        }
        
        // 按成功率排序
        available.sort((a, b) -> Double.compare(b.getSuccessRate(), a.getSuccessRate()));
        return available;
    }
    
    // 创建雇佣会话
    public HireSession createHireSession(Player buyer, UUID assassinId, UUID targetId) {
        AssassinProfile profile = assassinProfiles.get(assassinId);
        if (profile == null) {
            buyer.sendMessage(ChatColor.RED + "❌ 刺客不存在或已注销");
            return null;
        }
        
        Player assassin = Bukkit.getPlayer(assassinId);
        if (assassin == null || !assassin.isOnline()) {
            buyer.sendMessage(ChatColor.RED + "❌ 刺客不在线");
            return null;
        }
        
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            buyer.sendMessage(ChatColor.RED + "❌ 目标玩家不在线");
            return null;
        }
        
        UUID sessionId = UUID.randomUUID();
        HireSession session = new HireSession(
            sessionId,
            buyer.getUniqueId(),
            assassinId,
            System.currentTimeMillis()
        );
        
        session.setTargetId(targetId);
        activeSessions.put(sessionId, session);
        
        // 给予买家通讯书
        giveCommunicationBook(buyer, sessionId, assassinId, false);
        
        // 给予刺客通讯书
        giveCommunicationBook(assassin, sessionId, buyer.getUniqueId(), true);
        
        // 通知双方
        buyer.sendMessage(ChatColor.GREEN + "📝 雇佣会话已创建！请使用通讯书与刺客沟通");
        assassin.sendMessage(ChatColor.GREEN + "💰 你有新的雇佣邀请！目标: " + target.getName());
        assassin.sendMessage(ChatColor.YELLOW + "💡 请使用通讯书查看详情并报价");
        
        saveHireSession(session);
        return session;
    }
    
    // 给予通讯书
    private void giveCommunicationBook(Player player, UUID sessionId, UUID otherPartyId, boolean isAssassin) {
        ItemStack book = CommunicationBook.createCommunicationBook(sessionId, otherPartyId, isAssassin);
        
        // 尝试添加到背包，如果满了就掉落
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(book);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), book);
        }
        
        String role = isAssassin ? "刺客" : "雇主";
        player.sendMessage(ChatColor.YELLOW + "📖 你获得了与" + role + "的通讯书");
    }
    
    // 刺客报价
    public boolean makeOffer(Player assassin, UUID sessionId, int amount) {
        HireSession session = activeSessions.get(sessionId);
        if (session == null) {
            assassin.sendMessage(ChatColor.RED + "❌ 雇佣会话不存在或已过期");
            return false;
        }
        
        if (!session.getAssassinId().equals(assassin.getUniqueId())) {
            assassin.sendMessage(ChatColor.RED + "❌ 这不是你的雇佣会话");
            return false;
        }
        
        if (session.getStatus() != HireSession.SessionStatus.NEGOTIATING) {
            assassin.sendMessage(ChatColor.RED + "❌ 当前无法报价");
            return false;
        }
        
        // 设置报价
        session.setOfferedAmount(amount);
        session.setStatus(HireSession.SessionStatus.OFFER_MADE);
        
        // 通知买家
        Player buyer = Bukkit.getPlayer(session.getBuyerId());
        if (buyer != null) {
            buyer.sendMessage(ChatColor.GOLD + "💰 刺客报价: " + amount + " 点信用点");
            buyer.sendMessage(ChatColor.YELLOW + "💡 使用通讯书接受或拒绝报价");
        }
        
        assassin.sendMessage(ChatColor.GREEN + "✅ 报价已发送: " + amount + " 点信用点");
        
        updateHireSession(session);
        return true;
    }
    
    // 接受报价并创建合约
    public boolean acceptOffer(Player buyer, UUID sessionId) {
        HireSession session = activeSessions.get(sessionId);
        if (session == null) {
            buyer.sendMessage(ChatColor.RED + "❌ 雇佣会话不存在或已过期");
            return false;
        }
        
        if (!session.getBuyerId().equals(buyer.getUniqueId())) {
            buyer.sendMessage(ChatColor.RED + "❌ 这不是你的雇佣会话");
            return false;
        }
        
        if (session.getStatus() != HireSession.SessionStatus.OFFER_MADE) {
            buyer.sendMessage(ChatColor.RED + "❌ 当前无法接受报价");
            return false;
        }
        
        int amount = session.getOfferedAmount();
        
        // 检查买家信用点是否足够
        if (getPlayerCredits(buyer) < amount) {
            buyer.sendMessage(ChatColor.RED + "❌ 信用点不足！需要 " + amount + " 点");
            return false;
        }
        
        // 冻结信用点
        if (!removeCredits(buyer, amount)) {
            buyer.sendMessage(ChatColor.RED + "❌ 信用点冻结失败");
            return false;
        }
        
        // 创建合约
        UUID contractId = UUID.randomUUID();
        HireContract contract = new HireContract(
            contractId,
            session.getBuyerId(),
            session.getAssassinId(),
            session.getTargetId(),
            amount,
            System.currentTimeMillis()
        );
        
        activeContracts.put(contractId, contract);
        
        // 更新会话状态
        session.setStatus(HireSession.SessionStatus.CONTRACT_CREATED);
        session.setContractId(contractId);
        
        // 给予刺客追踪指南针
        Player assassin = Bukkit.getPlayer(session.getAssassinId());
        if (assassin != null) {
            giveTrackingCompass(assassin, contractId, session.getTargetId());
            assassin.sendMessage(ChatColor.GREEN + "🎯 合约已成立！获得追踪指南针");
            assassin.sendMessage(ChatColor.YELLOW + "💰 目标: " + amount + " 点信用点");
        }
        
        // 通知双方
        buyer.sendMessage(ChatColor.GREEN + "✅ 合约已成立！刺客已出发");
        if (session.getTargetId().equals(buyer.getUniqueId())) {
            buyer.sendMessage(ChatColor.RED + "⚠ 警告：你将自己设为目标！");
        }
        
        // 清理会话
        activeSessions.remove(sessionId);
        removeHireSession(sessionId);
        
        saveHireContract(contract);
        return true;
    }
    
    // 给予追踪指南针
    private void giveTrackingCompass(Player assassin, UUID contractId, UUID targetId) {
        ItemStack compass = TrackingCompass.createTrackingCompass(contractId, targetId);
        
        HashMap<Integer, ItemStack> leftover = assassin.getInventory().addItem(compass);
        if (!leftover.isEmpty()) {
            assassin.getWorld().dropItemNaturally(assassin.getLocation(), compass);
        }
    }
    
    // 处理目标死亡
    public void handleTargetDeath(Player target, Player killer) {
        // 查找目标相关的活跃合约
        for (HireContract contract : activeContracts.values()) {
            if (contract.getTargetId().equals(target.getUniqueId()) && 
                contract.getStatus() == HireContract.ContractStatus.ACTIVE) {
                
                // 检查是否是合约刺客杀死的
                if (killer != null && killer.getUniqueId().equals(contract.getAssassinId())) {
                    // 合约成功
                    completeContract(contract, true, killer);
                } else {
                    // 非合约相关死亡，不影响合约
                    continue;
                }
            }
        }
    }
    
    // 处理刺客死亡
    public void handleAssassinDeath(Player assassin) {
        // 查找刺客相关的活跃合约
        for (HireContract contract : activeContracts.values()) {
            if (contract.getAssassinId().equals(assassin.getUniqueId()) && 
                contract.getStatus() == HireContract.ContractStatus.ACTIVE) {
                
                // 合约失败
                completeContract(contract, false, null);
            }
        }
    }
    
    // 完成合约
    private void completeContract(HireContract contract, boolean success, Player killer) {
        contract.setCompleted(true);
        contract.setSuccess(success);
        contract.setCompletionTime(System.currentTimeMillis());
        
        Player buyer = Bukkit.getPlayer(contract.getBuyerId());
        Player assassin = Bukkit.getPlayer(contract.getAssassinId());
        Player target = Bukkit.getPlayer(contract.getTargetId());
        
        if (success) {
            // 合约成功
            int contractAmount = contract.getAmount();
            int targetCredits = target != null ? getPlayerCredits(target) : 0;
            
            // 转移信用点
            if (target != null && targetCredits > 0) {
                // 目标信用点转移给买家
                removeCredits(target, targetCredits);
                addCredits(buyer, targetCredits);
            }
            
            // 合约金额支付给刺客
            addCredits(assassin, contractAmount);
            
            // 更新刺客档案
            AssassinProfile profile = assassinProfiles.get(contract.getAssassinId());
            if (profile != null) {
                profile.addCompletedContract(true);
                updateAssassinProfile(profile);
            }
            
            // 通知各方
            if (buyer != null) {
                buyer.sendMessage(ChatColor.GREEN + "✅ 合约完成！获得目标 " + targetCredits + " 点信用点");
            }
            if (assassin != null) {
                assassin.sendMessage(ChatColor.GREEN + "💰 合约完成！获得 " + contractAmount + " 点信用点");
            }
            if (target != null) {
                target.sendMessage(ChatColor.RED + "💀 你被雇佣刺客终结了！信用点被转移");
            }
            
        } else {
            // 合约失败
            int contractAmount = contract.getAmount();
            
            // 返还冻结金额给买家
            addCredits(buyer, contractAmount);
            
            // 更新刺客档案
            AssassinProfile profile = assassinProfiles.get(contract.getAssassinId());
            if (profile != null) {
                profile.addCompletedContract(false);
                updateAssassinProfile(profile);
            }
            
            // 通知各方
            if (buyer != null) {
                buyer.sendMessage(ChatColor.RED + "❌ 合约失败！刺客死亡，金额已返还");
            }
            if (assassin != null) {
                assassin.sendMessage(ChatColor.RED + "💀 合约失败！你被目标反杀了");
            }
        }
        
        // 移除追踪指南针
        if (assassin != null) {
            removeTrackingCompass(assassin, contract.getContractId());
        }
        
        updateHireContract(contract);
        activeContracts.remove(contract.getContractId());
    }
    
    // 移除追踪指南针
    private void removeTrackingCompass(Player player, UUID contractId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (TrackingCompass.isTrackingCompass(item)) {
                UUID itemContractId = TrackingCompass.getContractId(item);
                if (itemContractId != null && itemContractId.equals(contractId)) {
                    player.getInventory().remove(item);
                    break;
                }
            }
        }
    }
    
    // 检查活跃合约状态
    public void checkActiveContracts() {
        Iterator<Map.Entry<UUID, HireContract>> iterator = activeContracts.entrySet().iterator();
        long currentTime = System.currentTimeMillis();
        
        while (iterator.hasNext()) {
            Map.Entry<UUID, HireContract> entry = iterator.next();
            HireContract contract = entry.getValue();
            
            // 检查合约超时
            if (currentTime - contract.getCreatedTime() > contractTimeout) {
                // 合约超时
                completeContract(contract, false, null);
                iterator.remove();
            }
            
            // 检查目标或刺客是否离线
            Player target = Bukkit.getPlayer(contract.getTargetId());
            Player assassin = Bukkit.getPlayer(contract.getAssassinId());
            
            if (target == null || !target.isOnline() || 
                assassin == null || !assassin.isOnline()) {
                // 一方离线，合约失败
                completeContract(contract, false, null);
                iterator.remove();
            }
        }
        
        // 检查会话超时
        Iterator<Map.Entry<UUID, HireSession>> sessionIterator = activeSessions.entrySet().iterator();
        while (sessionIterator.hasNext()) {
            Map.Entry<UUID, HireSession> entry = sessionIterator.next();
            HireSession session = entry.getValue();
            
            if (currentTime - session.getCreatedTime() > 3600000) { // 1小时超时
                sessionIterator.remove();
                removeHireSession(session.getSessionId());
            }
        }
    }
    
    // 信用点操作工具方法（通过反射调用CreditPlugin）
    private int getPlayerCredits(Player player) {
        try {
            Object creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin == null) return 0;
            
            Method getCreditManager = creditPlugin.getClass().getMethod("getCreditManager");
            Object creditManager = getCreditManager.invoke(creditPlugin);
            
            Method getCredits = creditManager.getClass().getMethod("getCredits", Player.class);
            return (int) getCredits.invoke(creditManager, player);
            
        } catch (Exception e) {
            PlayerHirePlugin.getInstance().getLogger().warning("获取信用点时出错: " + e.getMessage());
            return 0;
        }
    }
    
    private boolean removeCredits(Player player, int amount) {
        try {
            Object creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin == null) return false;
            
            Method getCreditManager = creditPlugin.getClass().getMethod("getCreditManager");
            Object creditManager = getCreditManager.invoke(creditPlugin);
            
            Method removeCredits = creditManager.getClass().getMethod("removeCredits", Player.class, int.class);
            return (boolean) removeCredits.invoke(creditManager, player, amount);
            
        } catch (Exception e) {
            PlayerHirePlugin.getInstance().getLogger().warning("扣除信用点时出错: " + e.getMessage());
            return false;
        }
    }
    
    private void addCredits(Player player, int amount) {
        try {
            Object creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin == null) return;
            
            Method getCreditManager = creditPlugin.getClass().getMethod("getCreditManager");
            Object creditManager = getCreditManager.invoke(creditPlugin);
            
            Method addCredits = creditManager.getClass().getMethod("addCredits", Player.class, int.class);
            addCredits.invoke(creditManager, player, amount);
            
        } catch (Exception e) {
            PlayerHirePlugin.getInstance().getLogger().warning("添加信用点时出错: " + e.getMessage());
        }
    }
    
    // 数据持久化方法
    public void saveAllData() {
        for (AssassinProfile profile : assassinProfiles.values()) {
            saveAssassinProfile(profile);
        }
        for (HireSession session : activeSessions.values()) {
            saveHireSession(session);
        }
        for (HireContract contract : activeContracts.values()) {
            saveHireContract(contract);
        }
        PlayerHirePlugin.getInstance().saveData();
    }
    
    private void saveAssassinProfile(AssassinProfile profile) {
        FileConfiguration config = PlayerHirePlugin.getInstance().getDataConfig();
        String path = "assassins." + profile.getPlayerId().toString();
        
        config.set(path + ".name", profile.getPlayerName());
        config.set(path + ".registered", profile.getRegisteredTime());
        config.set(path + ".completed", profile.getCompletedContracts());
        config.set(path + ".successful", profile.getSuccessfulContracts());
        config.set(path + ".failed", profile.getFailedContracts());
        config.set(path + ".totalEarned", profile.getTotalEarned());
    }
    
    private void updateAssassinProfile(AssassinProfile profile) {
        saveAssassinProfile(profile);
    }
    
    private void saveHireSession(HireSession session) {
        FileConfiguration config = PlayerHirePlugin.getInstance().getDataConfig();
        String path = "sessions." + session.getSessionId().toString();
        
        config.set(path + ".buyer", session.getBuyerId().toString());
        config.set(path + ".assassin", session.getAssassinId().toString());
        config.set(path + ".target", session.getTargetId().toString());
        config.set(path + ".created", session.getCreatedTime());
        config.set(path + ".status", session.getStatus().name());
        config.set(path + ".offeredAmount", session.getOfferedAmount());
        
        if (session.getContractId() != null) {
            config.set(path + ".contract", session.getContractId().toString());
        }
    }
    
    private void updateHireSession(HireSession session) {
        saveHireSession(session);
    }
    
    private void removeHireSession(UUID sessionId) {
        FileConfiguration config = PlayerHirePlugin.getInstance().getDataConfig();
        config.set("sessions." + sessionId.toString(), null);
    }
    
    private void saveHireContract(HireContract contract) {
        FileConfiguration config = PlayerHirePlugin.getInstance().getDataConfig();
        String path = "contracts." + contract.getContractId().toString();
        
        config.set(path + ".buyer", contract.getBuyerId().toString());
        config.set(path + ".assassin", contract.getAssassinId().toString());
        config.set(path + ".target", contract.getTargetId().toString());
        config.set(path + ".amount", contract.getAmount());
        config.set(path + ".created", contract.getCreatedTime());
        config.set(path + ".completed", contract.isCompleted());
        config.set(path + ".success", contract.isSuccess());
        
        if (contract.getCompletionTime() > 0) {
            config.set(path + ".completedTime", contract.getCompletionTime());
        }
    }
    
    private void updateHireContract(HireContract contract) {
        saveHireContract(contract);
    }
    
    private void loadAllData() {
        FileConfiguration config = PlayerHirePlugin.getInstance().getDataConfig();
        
        // 加载刺客档案
        if (config.contains("assassins")) {
            for (String playerIdStr : config.getConfigurationSection("assassins").getKeys(false)) {
                String path = "assassins." + playerIdStr;
                
                UUID playerId = UUID.fromString(playerIdStr);
                String playerName = config.getString(path + ".name");
                long registered = config.getLong(path + ".registered");
                int completed = config.getInt(path + ".completed");
                int successful = config.getInt(path + ".successful");
                int failed = config.getInt(path + ".failed");
                int totalEarned = config.getInt(path + ".totalEarned");
                
                AssassinProfile profile = new AssassinProfile(playerId, playerName, registered);
                profile.setCompletedContracts(completed);
                profile.setSuccessfulContracts(successful);
                profile.setFailedContracts(failed);
                profile.setTotalEarned(totalEarned);
                
                assassinProfiles.put(playerId, profile);
            }
        }
        
        // 加载活跃会话（只加载未过期的）
        if (config.contains("sessions")) {
            long currentTime = System.currentTimeMillis();
            for (String sessionIdStr : config.getConfigurationSection("sessions").getKeys(false)) {
                String path = "sessions." + sessionIdStr;
                
                UUID sessionId = UUID.fromString(sessionIdStr);
                UUID buyerId = UUID.fromString(config.getString(path + ".buyer"));
                UUID assassinId = UUID.fromString(config.getString(path + ".assassin"));
                UUID targetId = UUID.fromString(config.getString(path + ".target"));
                long created = config.getLong(path + ".created");
                
                // 检查是否过期（1小时）
                if (currentTime - created > 3600000) {
                    continue;
                }
                
                HireSession session = new HireSession(sessionId, buyerId, assassinId, created);
                session.setTargetId(targetId);
                session.setStatus(HireSession.SessionStatus.valueOf(config.getString(path + ".status")));
                session.setOfferedAmount(config.getInt(path + ".offeredAmount"));
                
                if (config.contains(path + ".contract")) {
                    session.setContractId(UUID.fromString(config.getString(path + ".contract")));
                }
                
                activeSessions.put(sessionId, session);
            }
        }
        
        // 加载活跃合约（只加载未完成的）
        if (config.contains("contracts")) {
            for (String contractIdStr : config.getConfigurationSection("contracts").getKeys(false)) {
                String path = "contracts." + contractIdStr;
                
                UUID contractId = UUID.fromString(contractIdStr);
                UUID buyerId = UUID.fromString(config.getString(path + ".buyer"));
                UUID assassinId = UUID.fromString(config.getString(path + ".assassin"));
                UUID targetId = UUID.fromString(config.getString(path + ".target"));
                int amount = config.getInt(path + ".amount");
                long created = config.getLong(path + ".created");
                boolean completed = config.getBoolean(path + ".completed");
                boolean success = config.getBoolean(path + ".success");
                
                if (completed) {
                    continue; // 跳过已完成的合约
                }
                
                HireContract contract = new HireContract(contractId, buyerId, assassinId, targetId, amount, created);
                contract.setCompleted(completed);
                contract.setSuccess(success);
                
                if (config.contains(path + ".completedTime")) {
                    contract.setCompletionTime(config.getLong(path + ".completedTime"));
                }
                
                activeContracts.put(contractId, contract);
            }
        }
    }
    
    // Getter方法
    public Map<UUID, AssassinProfile> getAssassinProfiles() {
        return assassinProfiles;
    }
    
    public Map<UUID, HireContract> getActiveContracts() {
        return activeContracts;
    }
    
    public Map<UUID, HireSession> getActiveSessions() {
        return activeSessions;
    }
    
    public int getRegistrationFee() {
        return registrationFee;
    }
    
    public long getContractTimeout() {
        return contractTimeout;
    }
    
    public long getCooldownTime() {
        return cooldownTime;
    }
}

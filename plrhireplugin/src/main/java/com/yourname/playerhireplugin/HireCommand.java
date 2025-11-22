package com.yourname.playerhireplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HireCommand implements CommandExecutor, TabCompleter {
    
    private final HireManager hireManager = PlayerHirePlugin.getInstance().getHireManager();
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                sendHelp((Player) sender);
            } else {
                sendConsoleHelp(sender);
            }
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "gui":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以使用此命令");
                    return true;
                }
                openHireGUI((Player) sender);
                break;
            case "offer":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以使用此命令");
                    return true;
                }
                handleMakeOffer((Player) sender, args);
                break;
            case "accept":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以使用此命令");
                    return true;
                }
                handleAcceptOffer((Player) sender, args);
                break;
            case "message":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以使用此命令");
                    return true;
                }
                handleSendMessage((Player) sender, args);
                break;
            case "contracts":
                handleListContracts(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "sessions":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以使用此命令");
                    return true;
                }
                handleListSessions((Player) sender);
                break;
            default:
                if (sender instanceof Player) {
                    sendHelp((Player) sender);
                } else {
                    sendConsoleHelp(sender);
                }
        }
        
        return true;
    }
    
    private void sendHelp(Player player) {
        player.sendMessage("§8=== §6玩家雇佣市场 §8===");
        player.sendMessage("§6/hire gui §7- 打开雇佣市场界面");
        player.sendMessage("§6/hire offer <会话ID> <金额> §7- 刺客报价");
        player.sendMessage("§6/hire accept <会话ID> §7- 接受报价");
        player.sendMessage("§6/hire message <会话ID> <消息> §7- 发送匿名消息");
        player.sendMessage("§6/hire contracts §7- 查看我的合约");
        player.sendMessage("§6/hire sessions §7- 查看我的会话");
        player.sendMessage("§8——————————————");
        player.sendMessage("§e使用说明:");
        player.sendMessage("§71. 通过GUI选择刺客和目标");
        player.sendMessage("§72. 使用/hire message发送消息沟通");
        player.sendMessage("§73. 刺客使用/hire offer报价");
        player.sendMessage("§74. 买家使用/hire accept接受");
        player.sendMessage("§75. 刺客获得追踪指南针执行任务");
    }
    
    private void sendConsoleHelp(CommandSender sender) {
        sender.sendMessage("§8=== §6玩家雇佣市场 §8===");
        sender.sendMessage("§6/hire reload §7- 重载配置");
        sender.sendMessage("§6/hire contracts <玩家> §7- 查看玩家合约");
    }
    
    private void openHireGUI(Player player) {
        HireGUI.openMainMenu(player);
    }
    
    private void handleMakeOffer(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c❌ 用法: /hire offer <会话ID> <金额>");
            player.sendMessage("§e💡 提示: 会话ID可以在通讯书中找到");
            player.sendMessage("§e💡 示例: /hire offer abcdef12 100");
            return;
        }
        
        try {
            String sessionIdStr = args[1];
            UUID sessionId = findSessionById(sessionIdStr, player);
            
            if (sessionId == null) {
                player.sendMessage("§c❌ 未找到匹配的会话，请检查:");
                player.sendMessage("§7• ID是否正确（使用通讯书中的前8位）");
                player.sendMessage("§7• 会话是否已过期");
                player.sendMessage("§7• 你是否是该会话的刺客");
                return;
            }
            
            int amount = Integer.parseInt(args[2]);
            
            if (amount <= 0) {
                player.sendMessage("§c❌ 金额必须大于0");
                return;
            }
            
            if (amount > 1000000) {
                player.sendMessage("§c❌ 金额不能超过1,000,000");
                return;
            }
            
            // 检查玩家信用点是否足够
            
            if (hireManager.makeOffer(player, sessionId, amount)) {
                player.sendMessage("§a✅ 报价已发送！金额: " + amount + " 信用点");
                
                // 通知买家
                HireSession session = hireManager.getActiveSessions().get(sessionId);
                if (session != null) {
                    Player buyer = Bukkit.getPlayer(session.getBuyerId());
                    if (buyer != null) {
                        buyer.sendMessage("§6💰 刺客已报价: " + amount + " 信用点");
                        buyer.sendMessage("§e💡 使用 /hire accept " + sessionIdStr + " 接受报价");
                    }
                }
            } else {
                player.sendMessage("§c❌ 报价失败，请检查会话状态");
            }
            
        } catch (NumberFormatException e) {
            player.sendMessage("§c❌ 金额必须是有效的数字");
        }
    }
    
    private void handleAcceptOffer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c❌ 用法: /hire accept <会话ID>");
            player.sendMessage("§e💡 提示: 会话ID可以在通讯书中找到");
            player.sendMessage("§e💡 示例: /hire accept abcdef12");
            return;
        }
        
        try {
            String sessionIdStr = args[1];
            UUID sessionId = findSessionById(sessionIdStr, player);
            
            if (sessionId == null) {
                player.sendMessage("§c❌ 未找到匹配的会话，请检查:");
                player.sendMessage("§7• ID是否正确（使用通讯书中的前8位）");
                player.sendMessage("§7• 会话是否已过期");
                player.sendMessage("§7• 你是否是该会话的买家");
                return;
            }
            
            if (hireManager.acceptOffer(player, sessionId)) {
                player.sendMessage("§a✅ 合约已成立！刺客已出发执行任务");
            } else {
                player.sendMessage("§c❌ 接受报价失败，请检查:");
                player.sendMessage("§7• 你的信用点是否足够支付报价金额");
                player.sendMessage("§7• 会话状态是否允许接受报价");
            }
            
        } catch (Exception e) {
            player.sendMessage("§c❌ 发生错误: " + e.getMessage());
        }
    }
    
    // 新增方法：处理发送消息
    private void handleSendMessage(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c❌ 用法: /hire message <会话ID> <消息内容>");
            player.sendMessage("§e💡 提示: 会话ID可以在通讯书中找到");
            player.sendMessage("§e💡 示例: /hire message abcdef12 你好，我们可以谈谈价格吗？");
            return;
        }
        
        try {
            String sessionIdStr = args[1];
            UUID sessionId = findSessionById(sessionIdStr, player);
            
            if (sessionId == null) {
                player.sendMessage("§c❌ 未找到匹配的会话，请检查:");
                player.sendMessage("§7• ID是否正确（使用通讯书中的前8位）");
                player.sendMessage("§7• 会话是否已过期");
                player.sendMessage("§7• 你是否是该会话的参与者");
                return;
            }
            
            // 组合消息内容（从第三个参数开始）
            StringBuilder messageBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                messageBuilder.append(args[i]).append(" ");
            }
            String message = messageBuilder.toString().trim();
            
            if (message.isEmpty()) {
                player.sendMessage("§c❌ 消息内容不能为空");
                return;
            }
            
            if (message.length() > 200) {
                player.sendMessage("§c❌ 消息内容不能超过200个字符");
                return;
            }
            
            // 发送消息
            if (hireManager.sendMessage(sessionId, player, message)) {
                player.sendMessage("§a✅ 消息已发送");
            } else {
                player.sendMessage("§c❌ 消息发送失败");
            }
            
        } catch (Exception e) {
            player.sendMessage("§c❌ 发生错误: " + e.getMessage());
        }
    }
    
    // 新增方法：通过ID查找会话（支持完整UUID和短ID）
    private UUID findSessionById(String id, Player player) {
        // 首先尝试作为完整UUID解析
        try {
            UUID fullUUID = UUID.fromString(id);
            HireSession session = hireManager.getActiveSessions().get(fullUUID);
            if (session != null && isPlayerInSession(session, player)) {
                return fullUUID;
            }
        } catch (IllegalArgumentException e) {
            // 不是完整UUID，继续尝试短ID
        }
        
        // 尝试作为短ID查找
        for (UUID sessionId : hireManager.getActiveSessions().keySet()) {
            if (sessionId.toString().startsWith(id)) {
                HireSession session = hireManager.getActiveSessions().get(sessionId);
                if (session != null && isPlayerInSession(session, player)) {
                    return sessionId;
                }
            }
        }
        
        return null;
    }
    
    // 检查玩家是否在会话中
    private boolean isPlayerInSession(HireSession session, Player player) {
        return session.getBuyerId().equals(player.getUniqueId()) || 
               session.getAssassinId().equals(player.getUniqueId());
    }
    
    // 添加信用点检查方法
    private int getPlayerCredits(Player player) {
        try {
            Object creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin == null) return 0;
            
            java.lang.reflect.Method getCreditManager = creditPlugin.getClass().getMethod("getCreditManager");
            Object creditManager = getCreditManager.invoke(creditPlugin);
            
            java.lang.reflect.Method getCredits = creditManager.getClass().getMethod("getCredits", Player.class);
            return (int) getCredits.invoke(creditManager, player);
            
        } catch (Exception e) {
            return 0;
        }
    }
    
    // 新增方法：列出玩家的会话
    private void handleListSessions(Player player) {
        player.sendMessage("§8=== §6你的雇佣会话 §8===");
        
        boolean hasSessions = false;
        for (HireSession session : hireManager.getActiveSessions().values()) {
            if (session.getBuyerId().equals(player.getUniqueId()) || 
                session.getAssassinId().equals(player.getUniqueId())) {
                
                hasSessions = true;
                String sessionId = session.getSessionId().toString();
                String shortId = sessionId.substring(0, 8);
                String role = session.getBuyerId().equals(player.getUniqueId()) ? "买家" : "刺客";
                String status = getSessionStatusText(session.getStatus());
                
                player.sendMessage("§7会话ID: §e" + shortId + "§7... (" + role + ")");
                player.sendMessage("§7状态: " + status);
                
                if (session.getStatus() == HireSession.SessionStatus.OFFER_MADE) {
                    player.sendMessage("§7报价: §6" + session.getOfferedAmount() + " 信用点");
                    if (session.getBuyerId().equals(player.getUniqueId())) {
                        player.sendMessage("§a💡 使用: /hire accept " + shortId);
                    }
                } else if (session.getStatus() == HireSession.SessionStatus.NEGOTIATING) {
                    if (session.getAssassinId().equals(player.getUniqueId())) {
                        player.sendMessage("§a💡 使用: /hire offer " + shortId + " <金额>");
                    }
                }
                player.sendMessage("§a💡 使用: /hire message " + shortId + " <消息> 发送消息");
                player.sendMessage("");
            }
        }
        
        if (!hasSessions) {
            player.sendMessage("§7暂无活跃的雇佣会话");
            player.sendMessage("§7使用 §6/hire gui §7创建新的雇佣会话");
        }
    }
    
    private String getSessionStatusText(HireSession.SessionStatus status) {
        switch (status) {
            case NEGOTIATING: return "§e协商中";
            case OFFER_MADE: return "§6已报价";
            case CONTRACT_CREATED: return "§a合约已成立";
            case EXPIRED: return "§c已过期";
            default: return "§7未知";
        }
    }
    
    private void handleListContracts(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 1 && sender.hasPermission("hire.admin")) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§c❌ 玩家不存在或不在线");
                return;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage("§c❌ 控制台请指定玩家名");
            return;
        }
        
        // 获取玩家的合约信息
        int activeContracts = 0;
        int completedContracts = 0;
        int successfulContracts = 0;
        
        for (HireContract contract : hireManager.getActiveContracts().values()) {
            if (contract.getBuyerId().equals(target.getUniqueId()) || 
                contract.getAssassinId().equals(target.getUniqueId())) {
                activeContracts++;
            }
        }
        
        AssassinProfile profile = hireManager.getAssassinProfiles().get(target.getUniqueId());
        if (profile != null) {
            completedContracts = profile.getCompletedContracts();
            successfulContracts = profile.getSuccessfulContracts();
        }
        
        sender.sendMessage("§8=== §6" + target.getName() + "的合约信息 §8===");
        sender.sendMessage("§7活跃合约: §e" + activeContracts);
        sender.sendMessage("§7完成合约: §a" + completedContracts);
        sender.sendMessage("§7成功合约: §2" + successfulContracts);
        
        if (profile != null) {
            sender.sendMessage("§7成功率: §6" + String.format("%.1f", profile.getSuccessRate() * 100) + "%");
            sender.sendMessage("§7总收入: §e" + profile.getTotalEarned() + " 信用点");
        }
    }
    
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("hire.admin")) {
            sender.sendMessage("§c❌ 你没有权限重载配置");
            return;
        }
        
        hireManager.reloadConfig();
        sender.sendMessage("§a✅ 玩家雇佣市场配置已重载");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("gui");
            completions.add("offer");
            completions.add("accept");
            completions.add("message");
            completions.add("contracts");
            completions.add("sessions");
            if (sender.hasPermission("hire.admin")) {
                completions.add("reload");
            }
        } else if (args.length == 2 && "contracts".equals(args[0]) && sender.hasPermission("hire.admin")) {
            // 为contracts提供玩家名补全
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 2 && ("offer".equals(args[0]) || "accept".equals(args[0]) || "message".equals(args[0]))) {
            // 为offer、accept和message提供会话ID补全（仅限玩家自己的会话）
            if (sender instanceof Player) {
                Player player = (Player) sender;
                for (HireSession session : hireManager.getActiveSessions().values()) {
                    if (session.getBuyerId().equals(player.getUniqueId()) || 
                        session.getAssassinId().equals(player.getUniqueId())) {
                        String shortId = session.getSessionId().toString().substring(0, 8);
                        completions.add(shortId);
                    }
                }
            }
        }
        
        return completions;
    }
}

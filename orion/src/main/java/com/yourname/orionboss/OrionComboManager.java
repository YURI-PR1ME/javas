package com.yourname.orionboss;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class OrionComboManager {
    
    private final JavaPlugin plugin;
    private FileConfiguration comboConfig;
    private File comboFile;
    
    // 连招定义
    private final Map<String, List<List<Integer>>> comboMap = new HashMap<>();
    private final Map<String, List<List<Integer>>> lowHealthComboMap = new HashMap<>();
    
    // 当前连招状态
    private List<Integer> currentCombo = new ArrayList<>();
    private int comboStep = 0;
    private long lastComboTime = 0;
    private static final long COMBO_RESET_TIME = 10000; // 10秒未完成连招则重置
    private final Random random = new Random();
    
    // 连招计数器
    private int comboCount = 0;
    
    public OrionComboManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadCombos();
    }
    
    private void loadCombos() {
        // 默认连招配置
        comboMap.put("normal", Arrays.asList(
            Arrays.asList(1, -1, 2),      // Lava -> A -> Skull
            Arrays.asList(2, -1, -1, 3),  // Skull -> A -> A -> Clone
            Arrays.asList(4, -1, 5),      // Void -> A -> Crystal
            Arrays.asList(6, -1, -1, 1),  // Rain -> A -> A -> Lava
            Arrays.asList(3, -1, 2, -1, 4) // Clone -> A -> Skull -> A -> Void
        ));
        
        comboMap.put("crucial", Arrays.asList(
            Arrays.asList(7),             // Ultimate
            Arrays.asList(8)              // Execution
        ));
        
        // 血量<50%的特殊连招（根据您的要求）
        lowHealthComboMap.put("special", Arrays.asList(
            Arrays.asList(1, 2),          // 1,2/3/5/6 -> 取1,2
            Arrays.asList(1, 3),          // 1,2/3/5/6 -> 取1,3
            Arrays.asList(1, 5),          // 1,2/3/5/6 -> 取1,5
            Arrays.asList(1, 6),          // 1,2/3/5/6 -> 取1,6
            Arrays.asList(2, 6, 3),       // 2,6,3
            Arrays.asList(4),             // 4
            Arrays.asList(2),             // 2
            Arrays.asList(3),             // 3
            Arrays.asList(1),             // 1
            Arrays.asList(-1, -1, -1)     // 普通攻击连接
        ));
        
        // 尝试从配置文件加载
        loadFromConfig();
    }
    
    private void loadFromConfig() {
        comboFile = new File(plugin.getDataFolder(), "combos.yml");
        if (!comboFile.exists()) {
            plugin.saveResource("combos.yml", false);
        }
        comboConfig = YamlConfiguration.loadConfiguration(comboFile);
        
        // 加载自定义连招
        loadCustomCombos();
    }
    
    private void loadCustomCombos() {
        if (comboConfig.getConfigurationSection("normal_combos") != null) {
            List<List<Integer>> customNormal = new ArrayList<>();
            for (String key : comboConfig.getConfigurationSection("normal_combos").getKeys(false)) {
                String comboString = comboConfig.getString("normal_combos." + key);
                List<Integer> combo = parseComboString(comboString);
                if (!combo.isEmpty()) {
                    customNormal.add(combo);
                }
            }
            if (!customNormal.isEmpty()) {
                comboMap.put("normal", customNormal);
            }
        }
        
        if (comboConfig.getConfigurationSection("low_health_combos") != null) {
            List<List<Integer>> customLowHealth = new ArrayList<>();
            for (String key : comboConfig.getConfigurationSection("low_health_combos").getKeys(false)) {
                String comboString = comboConfig.getString("low_health_combos." + key);
                List<Integer> combo = parseComboString(comboString);
                if (!combo.isEmpty()) {
                    customLowHealth.add(combo);
                }
            }
            if (!customLowHealth.isEmpty()) {
                lowHealthComboMap.put("special", customLowHealth);
            }
        }
    }
    
    private List<Integer> parseComboString(String comboString) {
        List<Integer> combo = new ArrayList<>();
        if (comboString == null || comboString.isEmpty()) return combo;
        
        String[] parts = comboString.split(",");
        for (String part : parts) {
            try {
                combo.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid combo element: " + part);
            }
        }
        return combo;
    }
    
    // 获取下一个连招
    public List<Integer> getNextCombo(double healthPercent) {
        // 如果有未完成的连招且未超时，继续执行
        if (!currentCombo.isEmpty() && comboStep < currentCombo.size()) {
            if (System.currentTimeMillis() - lastComboTime > COMBO_RESET_TIME) {
                resetCombo();
            } else {
                return currentCombo;
            }
        }
        
        // 选择新的连招
        List<List<Integer>> availableCombos = new ArrayList<>();
        
        if (healthPercent < 0.5) {
            // 血量<50%：可以执行所有连招
            availableCombos.addAll(comboMap.getOrDefault("crucial", new ArrayList<>()));
            availableCombos.addAll(lowHealthComboMap.getOrDefault("special", new ArrayList<>()));
        } else {
            // 血量≥50%：只执行普通连招
            availableCombos.addAll(comboMap.getOrDefault("normal", new ArrayList<>()));
        }
        
        if (availableCombos.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 随机选择一个连招
        currentCombo = new ArrayList<>(availableCombos.get(random.nextInt(availableCombos.size())));
        comboStep = 0;
        lastComboTime = System.currentTimeMillis();
        comboCount++;
        
        return currentCombo;
    }
    
    // 获取连招中的下一个技能
    public int getNextSkillInCombo() {
        if (currentCombo.isEmpty() || comboStep >= currentCombo.size()) {
            return 0; // 连招结束
        }
        
        int skill = currentCombo.get(comboStep);
        comboStep++;
        lastComboTime = System.currentTimeMillis();
        
        // 如果连招完成，准备下一个
        if (comboStep >= currentCombo.size()) {
            comboStep = 0;
            currentCombo.clear();
        }
        
        return skill;
    }
    
    // 重置连招
    public void resetCombo() {
        currentCombo.clear();
        comboStep = 0;
    }
    
    // 获取连招计数器
    public int getComboCount() {
        return comboCount;
    }
    
    // 重置连招计数器
    public void resetComboCount() {
        comboCount = 0;
    }
    
    // 自定义添加连招
    public void addCustomCombo(String type, List<Integer> combo) {
        switch (type.toLowerCase()) {
            case "normal":
                comboMap.get("normal").add(combo);
                break;
            case "crucial":
                comboMap.get("crucial").add(combo);
                break;
            case "lowhealth":
                lowHealthComboMap.get("special").add(combo);
                break;
        }
        saveToConfig();
    }
    
    private void saveToConfig() {
        // 保存到配置文件
        int normalIndex = 1;
        for (List<Integer> combo : comboMap.get("normal")) {
            comboConfig.set("normal_combos.combo_" + normalIndex, comboToString(combo));
            normalIndex++;
        }
        
        int lowHealthIndex = 1;
        for (List<Integer> combo : lowHealthComboMap.get("special")) {
            comboConfig.set("low_health_combos.combo_" + lowHealthIndex, comboToString(combo));
            lowHealthIndex++;
        }
        
        try {
            comboConfig.save(comboFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Error saving combo config: " + e.getMessage());
        }
    }
    
    private String comboToString(List<Integer> combo) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < combo.size(); i++) {
            sb.append(combo.get(i));
            if (i < combo.size() - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }
    
    // 获取当前连招信息
    public String getComboInfo() {
        if (currentCombo.isEmpty()) {
            return "无连招";
        }
        return "连招进度: " + comboStep + "/" + currentCombo.size() + 
               " [" + comboToString(currentCombo) + "]";
    }
}

package com.yourname.tyrantpickaxe;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class TyrantPickaxePlugin extends JavaPlugin {

    private static TyrantPickaxePlugin instance;
    private NamespacedKey tyrantPickaxeKey;
    private TyrantPickaxeManager pickaxeManager;

    @Override
    public void onEnable() {
        instance = this;
        tyrantPickaxeKey = new NamespacedKey(this, "tyrant_pickaxe");
        pickaxeManager = new TyrantPickaxeManager(this);
        
        getServer().getPluginManager().registerEvents(new TyrantPickaxeListener(this), this);
        getLogger().info("TyrantPickaxe plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        pickaxeManager.cleanup();
        getLogger().info("TyrantPickaxe plugin has been disabled!");
    }

    public static TyrantPickaxePlugin getInstance() {
        return instance;
    }

    public NamespacedKey getTyrantPickaxeKey() {
        return tyrantPickaxeKey;
    }

    public TyrantPickaxeManager getPickaxeManager() {
        return pickaxeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("tyrantpickaxe")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return true;
        }

        Player player = (Player) sender;
        
        if (!player.hasPermission("tyrantpickaxe.give")) {
            player.sendMessage("You don't have permission to use this command.");
            return true;
        }

        ItemStack tyrantPickaxe = createTyrantPickaxe();
        player.getInventory().addItem(tyrantPickaxe);
        player.sendMessage("You have received the Tyrant Pickaxe!");
        
        return true;
    }

    public ItemStack createTyrantPickaxe() {
        ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = pickaxe.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6Tyrant Pickaxe");
            meta.setLore(Arrays.asList(
                "§7A powerful pickaxe that bends space and time",
                "§7Grants Haste X when held",
                "§7Right-click to teleport behind entities",
                "§7Sneak + Right-click for rapid displacement",
                "§7Consecutive hits grant fire attacks and fireballs",
                "§7Beware: Power comes with consequences...",
                "",
                "§c\"Now, who is the Tyrant? HA Ha HA!\""
            ));
            
            meta.getPersistentDataContainer().set(tyrantPickaxeKey, PersistentDataType.BYTE, (byte) 1);
            pickaxe.setItemMeta(meta);
        }
        
        return pickaxe;
    }

    public boolean isTyrantPickaxe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(tyrantPickaxeKey);
    }
}

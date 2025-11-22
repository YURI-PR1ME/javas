package com.yourdomain.endsword;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class ItemManager {

    private final EndSword plugin;
    private ItemStack endSwordItem;
    private final NamespacedKey endSwordKey;

    public ItemManager(EndSword plugin) {
        this.plugin = plugin;
        this.endSwordKey = new NamespacedKey(plugin, "is_end_sword");
    }

    /**
     * 初始化物品
     */
    public void init() {
        endSwordItem = new ItemStack(Material.NETHERITE_SWORD); // 我们用下界合金剑作为基础
        ItemMeta meta = endSwordItem.getItemMeta();

        // 设置名称 (使用 Adventure 组件)
        meta.displayName(
                Component.text("终末之剑", NamedTextColor.DARK_PURPLE)
                        .decoration(TextDecoration.ITALIC, false)
        );

        // 设置 Lore
        meta.lore(List.of(
                Component.text("龙与雷电的力量汇聚于此。", NamedTextColor.GRAY)
        ));

        // 添加一个 NBT 标签 (PersistentDataContainer) 来识别这把剑
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(endSwordKey, PersistentDataType.BYTE, (byte) 1);

        endSwordItem.setItemMeta(meta);
    }

    /**
     * 创建合成配方
     */
    public void createRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(plugin, "end_sword_recipe");

        // 无序配方：龙蛋 + 木棍
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, endSwordItem);
        recipe.addIngredient(Material.DRAGON_EGG);
        recipe.addIngredient(Material.STICK);

        // 注册配方
        Bukkit.addRecipe(recipe);
    }

    /**
     * 检查一个物品是否是终末之剑
     * @param item 要检查的 ItemStack
     * @return 是/否
     */
    public boolean isEndSword(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        return data.has(endSwordKey, PersistentDataType.BYTE);
    }
}

package com.kaisaba.points.gui;

import com.kaisaba.points.KaisabaPoints;
import com.kaisaba.points.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Map;

public class ShopListGui {

    public static final String TITLE = "§8[ショップ一覧]";

    private final KaisabaPoints plugin;

    public ShopListGui(KaisabaPoints plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        int slot = 0;
        for (Map.Entry<String, ShopManager.ShopData> entry : plugin.getShopManager().getAllShops().entrySet()) {
            if (slot >= 54) break;
            ShopManager.ShopData shopData = entry.getValue();
            if (shopData.editMode) continue; // 編集中は非表示

            ItemStack icon = new ItemStack(Material.CHEST);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + shopData.displayName);
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(ChatColor.GRAY + "ID: " + shopData.id);
            if (shopData.owner != null && !shopData.owner.isEmpty()) {
                lore.add(ChatColor.GRAY + "オーナー: " + ChatColor.AQUA + shopData.owner);
            }
            lore.add(ChatColor.GREEN + "クリックで開く");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slot++;
        }

        player.openInventory(inv);
    }
}

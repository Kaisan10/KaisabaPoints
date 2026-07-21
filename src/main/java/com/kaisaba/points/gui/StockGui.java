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

public class StockGui {

    public static final String TITLE_PREFIX = "§8[在庫管理] §f";

    private final KaisabaPoints plugin;
    private final ShopManager.ShopData shopData;

    public StockGui(KaisabaPoints plugin, ShopManager.ShopData shopData) {
        this.plugin = plugin;
        this.shopData = shopData;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, getTitle(shopData.displayName));

        // 現在の在庫を並べる（0-44の45スロット）
        for (int i = 0; i < 45; i++) {
            if (shopData.stockInventory[i] != null) {
                inv.setItem(i, shopData.stockInventory[i].clone());
            }
        }

        // 最下行（45-52）: 黒ガラスパネルで封鎖
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 45; i < 53; i++) {
            inv.setItem(i, glass.clone());
        }

        // スロット53: 「戻る」ボタン
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "戻る");
        backMeta.setLore(Arrays.asList(ChatColor.GRAY + "管理メニューに戻る"));
        back.setItemMeta(backMeta);
        inv.setItem(53, back);

        player.openInventory(inv);
    }

    public ShopManager.ShopData getShopData() {
        return shopData;
    }

    public static String getTitle(String displayName) {
        return TITLE_PREFIX + displayName;
    }
}

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

public class AdminShopMenu {

    public static final String TITLE_PREFIX = "§6[マイショップ] §f";

    private final KaisabaPoints plugin;
    private final ShopManager.ShopData shopData;

    public AdminShopMenu(KaisabaPoints plugin, ShopManager.ShopData shopData) {
        this.plugin = plugin;
        this.shopData = shopData;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_PREFIX + shopData.displayName);

        // スロット10: 編集モード切替
        ItemStack editBtn = new ItemStack(Material.PAPER);
        ItemMeta editMeta = editBtn.getItemMeta();
        editMeta.setDisplayName(ChatColor.YELLOW + "編集モード切替");
        String status = shopData.editMode ? ChatColor.RED + "現在: ON（購入不可）" : ChatColor.GREEN + "現在: OFF（購入可）";
        editMeta.setLore(Arrays.asList(status, ChatColor.GRAY + "クリックで切り替え"));
        editBtn.setItemMeta(editMeta);
        inv.setItem(10, editBtn);

        // スロット13: アイテム設定
        ItemStack itemBtn = new ItemStack(Material.COMPASS);
        ItemMeta itemMeta = itemBtn.getItemMeta();
        itemMeta.setDisplayName(ChatColor.AQUA + "アイテム設定");
        itemMeta.setLore(Arrays.asList(ChatColor.GRAY + "クリックでアイテム設定メニューを開く"));
        itemBtn.setItemMeta(itemMeta);
        inv.setItem(13, itemBtn);

        // スロット16: 在庫管理
        ItemStack stockBtn = new ItemStack(Material.CHEST);
        ItemMeta stockMeta = stockBtn.getItemMeta();
        stockMeta.setDisplayName(ChatColor.GREEN + "在庫管理");
        stockMeta.setLore(Arrays.asList(ChatColor.GRAY + "クリックで在庫管理メニューを開く"));
        stockBtn.setItemMeta(stockMeta);
        inv.setItem(16, stockBtn);

        player.openInventory(inv);
    }

    public static String getTitle(String displayName) {
        return TITLE_PREFIX + displayName;
    }
}

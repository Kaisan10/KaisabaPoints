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

public class IdSetupGui {

    public static final String TITLE_PREFIX = "§8[ID設定] §f";

    // コンパスを固定するスロット番号（3段GUIの1段目中央）
    public static final int COMPASS_SLOT = 13;

    // 戻るボタンのスロット（3段GUIの最下段一番右）
    public static final int BACK_SLOT = 26;

    private final KaisabaPoints plugin;
    private final ShopManager.ShopData shopData;
    private final int itemSlot; // ItemSetupGui でクリックされたスロット番号
    private int currentId;

    public IdSetupGui(KaisabaPoints plugin, ShopManager.ShopData shopData, int itemSlot) {
        this.plugin = plugin;
        this.shopData = shopData;
        this.itemSlot = itemSlot;

        ShopManager.ShopItemData itemData = shopData.items.get(itemSlot);
        this.currentId = (itemData != null) ? itemData.apiProductId : 0;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, getTitle(shopData.displayName, itemSlot));

        // コンパス
        inv.setItem(COMPASS_SLOT, buildCompass());

        // 最下行(18-24): 黒ガラスパネルで封鎖
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 18; i < 26; i++) {
            inv.setItem(i, glass.clone());
        }

        // スロット26（最下段一番右）: 戻るボタン
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "戻る");
        backMeta.setLore(Arrays.asList(ChatColor.GRAY + "アイテム設定に戻る（IDを保存）"));
        back.setItemMeta(backMeta);
        inv.setItem(BACK_SLOT, back);

        player.openInventory(inv);
    }

    public ItemStack buildCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "API商品ID設定");
        meta.setLore(Arrays.asList(
            ChatColor.WHITE + "現在のAPI商品ID: " + ChatColor.AQUA + currentId,
            ChatColor.GRAY + "周りにアイテムを入れる → ID+1",
            ChatColor.GRAY + "コンパスをクリック → ID-1"
        ));
        compass.setItemMeta(meta);
        return compass;
    }

    public int getCurrentId() {
        return currentId;
    }

    public void incrementId() {
        currentId++;
    }

    public void decrementId() {
        if (currentId > 0) currentId--;
    }

    public int getItemSlot() {
        return itemSlot;
    }

    public ShopManager.ShopData getShopData() {
        return shopData;
    }

    public static String getTitle(String displayName, int itemSlot) {
        return TITLE_PREFIX + displayName + ":" + itemSlot;
    }
}

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

public class ItemSetupGui {

    public static final String TITLE_PREFIX = "§8[アイテム設定] §f";

    // ID選択モードかどうか（プレイヤーごと）
    private static final java.util.HashMap<String, Boolean> idModeMap = new java.util.HashMap<>();

    private final KaisabaPoints plugin;
    private final ShopManager.ShopData shopData;

    public ItemSetupGui(KaisabaPoints plugin, ShopManager.ShopData shopData) {
        this.plugin = plugin;
        this.shopData = shopData;
    }

    public void open(Player player) {
        open(player, isIdMode(player));
    }

    public void open(Player player, boolean idMode) {
        idModeMap.put(player.getName(), idMode);
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_PREFIX + shopData.displayName);

        // 既存のアイテムを配置（最下行には置かない）
        for (Map.Entry<Integer, ShopManager.ShopItemData> entry : shopData.items.entrySet()) {
            int slot = entry.getKey();
            if (slot >= 45) continue;
            ShopManager.ShopItemData itemData = entry.getValue();
            if (itemData.materialId != null) {
                ItemStack icon = buildIconFromMaterialId(itemData.materialId, itemData.apiProductId);
                if (icon != null) {
                    inv.setItem(slot, icon);
                }
            }
        }

        // 最下行: 黒ガラスパネルで封鎖（スロット45-51, 53）
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 45; i < 52; i++) {
            inv.setItem(i, glass.clone());
        }
        // スロット53: 戻るボタン
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "戻る");
        backMeta.setLore(Arrays.asList(ChatColor.GRAY + "編集メニューに戻る"));
        back.setItemMeta(backMeta);
        inv.setItem(53, back);

        // スロット52: ID選択モード切替トグルボタン
        inv.setItem(52, buildToggleButton(idMode));

        player.openInventory(inv);
    }

    /**
     * materialId (例: "DIAMOND_SWORD") からアイコン用 ItemStack を生成する。
     * Lore に api_product_id を表示する。
     *
     * @param materialId    Material 名文字列
     * @param apiProductId  商品ID（0 の場合は未設定として表示）
     * @return 生成した ItemStack。materialId が無効な場合は null。
     */
    public static ItemStack buildIconFromMaterialId(String materialId, int apiProductId) {
        if (materialId == null) return null;
        Material mat;
        try {
            mat = Material.valueOf(materialId);
        } catch (IllegalArgumentException e) {
            mat = Material.BARRIER; // 不明な Material はバリアで表示
        }
        ItemStack icon = new ItemStack(mat);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            if (apiProductId != 0) {
                meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "商品ID: " + ChatColor.YELLOW + apiProductId
                ));
            } else {
                meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "商品ID: " + ChatColor.RED + "未設定"
                ));
            }
            icon.setItemMeta(meta);
        }
        return icon;
    }

    /** ID選択モードのトグルボタンを生成する */
    public static ItemStack buildToggleButton(boolean idMode) {
        ItemStack btn;
        ItemMeta meta;
        if (idMode) {
            btn = new ItemStack(Material.NAME_TAG);
            meta = btn.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + "▶ IDモード: ON");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "アイテムをクリック → ID設定",
                ChatColor.GRAY + "クリックでモード切替"
            ));
        } else {
            btn = new ItemStack(Material.FEATHER);
            meta = btn.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + "▶ IDモード: OFF");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "アイテムをスロットに入れると登録されます",
                ChatColor.GRAY + "カーソルが空でクリック → 削除",
                ChatColor.GRAY + "クリックでIDモードに切替"
            ));
        }
        btn.setItemMeta(meta);
        return btn;
    }

    public static boolean isIdMode(Player player) {
        return idModeMap.getOrDefault(player.getName(), false);
    }

    public static void setIdMode(Player player, boolean mode) {
        idModeMap.put(player.getName(), mode);
    }

    public static void clearIdMode(Player player) {
        idModeMap.remove(player.getName());
    }

    public static String getTitle(String displayName) {
        return TITLE_PREFIX + displayName;
    }
}

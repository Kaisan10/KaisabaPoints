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
import java.util.List;

/**
 * 汎用確認GUI。
 * 削除確認・購入確認など用途に応じたファクトリメソッドを使って生成する。
 */
public class DeleteConfirmGui {

    // --- 削除確認 ---
    public static final String TITLE_PREFIX        = "§8[削除確認] §f";
    // --- 購入確認 ---
    public static final String PURCHASE_TITLE_PREFIX = "§8[購入確認] §f";

    /** OKボタン（緑ウール）のスロット */
    public static final int OK_SLOT     = 11;
    /** キャンセルボタン（赤ウール）のスロット */
    public static final int CANCEL_SLOT = 15;

    private final KaisabaPoints plugin;
    private final String title;
    private final Material infoMaterial;
    private final String infoName;
    private final List<String> infoLore;
    private final String okLabel;

    // ===== 内部コンストラクタ =====

    private DeleteConfirmGui(KaisabaPoints plugin,
                             String title,
                             Material infoMaterial,
                             String infoName,
                             List<String> infoLore,
                             String okLabel) {
        this.plugin       = plugin;
        this.title        = title;
        this.infoMaterial = infoMaterial;
        this.infoName     = infoName;
        this.infoLore     = infoLore;
        this.okLabel      = okLabel;
    }

    // ===== ファクトリ: 削除確認（既存互換） =====

    /**
     * 既存の削除確認GUI を生成する。
     * 呼び出し元のコードは変更不要。
     */
    public DeleteConfirmGui(KaisabaPoints plugin, ShopManager.ShopData shopData) {
        this(plugin,
             TITLE_PREFIX + shopData.displayName,
             Material.BOOK,
             ChatColor.YELLOW + "ショップを削除しますか？",
             Arrays.asList(
                 ChatColor.WHITE + "ID: " + ChatColor.AQUA + shopData.id,
                 ChatColor.WHITE + "表示名: " + ChatColor.AQUA + shopData.displayName,
                 "",
                 ChatColor.RED + "⚠ この操作は取り消せません！"
             ),
             ChatColor.GREEN + "✔ 削除する");
    }

    /**
     * 削除確認GUI ファクトリ（明示的な生成用）。
     */
    public static DeleteConfirmGui forDelete(KaisabaPoints plugin, ShopManager.ShopData shopData) {
        return new DeleteConfirmGui(plugin, shopData);
    }

    // ===== ファクトリ: 購入確認 =====

    /**
     * 購入確認GUI を生成する。
     *
     * @param plugin   プラグインインスタンス
     * @param shopData ショップデータ
     * @param itemData 購入するアイテムデータ
     * @param price    表示価格（pt）
     */
    public static DeleteConfirmGui forPurchase(KaisabaPoints plugin,
                                               ShopManager.ShopData shopData,
                                               ShopManager.ShopItemData itemData,
                                               int price) {
        Material mat;
        try {
            mat = Material.valueOf(itemData.materialId);
        } catch (IllegalArgumentException | NullPointerException ex) {
            mat = Material.CHEST;
        }

        return new DeleteConfirmGui(
            plugin,
            PURCHASE_TITLE_PREFIX + shopData.displayName,
            mat,
            ChatColor.YELLOW + "購入しますか？",
            Arrays.asList(
                ChatColor.WHITE + "ショップ: " + ChatColor.AQUA + shopData.displayName,
                ChatColor.WHITE + "価格: " + ChatColor.YELLOW + price + " pt",
                "",
                ChatColor.GREEN + "OKをクリックで購入確定"
            ),
            ChatColor.GREEN + "✔ 購入する"
        );
    }

    // ===== GUI構築 =====

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // スロット13: 情報アイテム
        ItemStack info = new ItemStack(infoMaterial);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(infoName);
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(13, info);

        // スロット11: OKボタン（緑ウール）
        ItemStack ok = new ItemStack(Material.GREEN_WOOL);
        ItemMeta okMeta = ok.getItemMeta();
        okMeta.setDisplayName(okLabel);
        okMeta.setLore(Arrays.asList(ChatColor.GRAY + "クリックで確定します"));
        ok.setItemMeta(okMeta);
        inv.setItem(OK_SLOT, ok);

        // スロット15: キャンセルボタン（赤ウール）
        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "✖ キャンセル");
        cancelMeta.setLore(Arrays.asList(ChatColor.GRAY + "操作をキャンセルします"));
        cancel.setItemMeta(cancelMeta);
        inv.setItem(CANCEL_SLOT, cancel);

        // 残りスロットを黒ガラスパネルで埋める
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glass.clone());
            }
        }

        player.openInventory(inv);
    }

    // ===== タイトル取得ユーティリティ =====

    /** 削除確認GUIのタイトルを返す（既存互換） */
    public static String getTitle(String displayName) {
        return TITLE_PREFIX + displayName;
    }

    /** 購入確認GUIのタイトルを返す */
    public static String getPurchaseTitle(String displayName) {
        return PURCHASE_TITLE_PREFIX + displayName;
    }
}

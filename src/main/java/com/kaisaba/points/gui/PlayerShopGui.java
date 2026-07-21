package com.kaisaba.points.gui;

import com.kaisaba.points.KaisabaPoints;
import com.kaisaba.points.PointsAPI;
import com.kaisaba.points.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlayerShopGui {

    public static final String TITLE_PREFIX = "§8[ショップ] §f";

    private final KaisabaPoints plugin;
    private final ShopManager.ShopData shopData;

    public PlayerShopGui(KaisabaPoints plugin, ShopManager.ShopData shopData) {
        this.plugin = plugin;
        this.shopData = shopData;
    }

    public void open(Player player) {
        // 非同期で商品一覧を取得してからGUIを開く（メインスレッドのブロッキングを防止）
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PointsAPI.ProductResponse> products;
            if (shopData.owner != null && !shopData.owner.isEmpty()) {
                products = plugin.getPointsAPI().getProducts(shopData.owner).join();
            } else {
                products = plugin.getProducts();
            }

            final List<PointsAPI.ProductResponse> finalProducts = products;
            Bukkit.getScheduler().runTask(plugin, () -> openWithProducts(player, finalProducts));
        });
    }

    private void openWithProducts(Player player, List<PointsAPI.ProductResponse> products) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_PREFIX + shopData.displayName);

        int stockCount = plugin.getShopManager().getStockCount(shopData);

        for (Map.Entry<Integer, ShopManager.ShopItemData> entry : shopData.items.entrySet()) {
            ShopManager.ShopItemData itemData = entry.getValue();
            if (itemData.materialId == null) continue;

            Material mat;
            try {
                mat = Material.valueOf(itemData.materialId);
            } catch (IllegalArgumentException ex) {
                mat = Material.BARRIER;
            }
            ItemStack icon = new ItemStack(mat);
            ItemMeta meta = icon.getItemMeta();
            if (meta == null) continue;

            // APIから商品情報を探す
            int price = 0;
            String productName = null;
            for (PointsAPI.ProductResponse product : products) {
                if (product.id == itemData.apiProductId) {
                    price = product.price;
                    productName = product.name;
                    break;
                }
            }

            // Loreを構築
            java.util.List<String> lore = new ArrayList<>();
            if (productName != null) {
                lore.add(ChatColor.WHITE + productName);
            }
            lore.add(ChatColor.GRAY + "価格: " + ChatColor.YELLOW + price + " pt");
            if (shopData.owner != null && !shopData.owner.isEmpty()) {
                lore.add(ChatColor.GRAY + "オーナー: " + ChatColor.AQUA + shopData.owner);
            } else {
                lore.add(ChatColor.GRAY + "オーナー: " + ChatColor.LIGHT_PURPLE + "サーバー公式");
            }
            if (stockCount == 0) {
                lore.add(ChatColor.RED + "在庫切れ");
            } else {
                lore.add(ChatColor.GRAY + "在庫: " + ChatColor.WHITE + stockCount + " 個");
            }
            lore.add("");
            lore.add(ChatColor.GREEN + "クリックで購入");

            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(entry.getKey(), icon);
        }

        player.openInventory(inv);
    }

    /**
     * 購入確認GUI を開く。
     * アイテムをクリックした直後に呼ばれ、在庫チェックをしてから確認GUIへ誘導する。
     */
    public void openPurchaseConfirm(Player player, ShopManager.ShopItemData itemData,
                                    List<PointsAPI.ProductResponse> products) {
        int stockCount = plugin.getShopManager().getStockCount(shopData);
        if (stockCount == 0) {
            player.sendMessage(ChatColor.RED + "[ショップ] 在庫切れです。");
            return;
        }

        // 価格を取得（ConfirmGUIの表示用）
        int price = 0;
        for (PointsAPI.ProductResponse product : products) {
            if (product.id == itemData.apiProductId) {
                price = product.price;
                break;
            }
        }

        DeleteConfirmGui confirmGui = DeleteConfirmGui.forPurchase(plugin, shopData, itemData, price);
        confirmGui.open(player);
    }

    /**
     * 購入フロー本体。確認GUI の OK ボタンから呼ばれる。
     */
    public void startPurchase(Player player, ShopManager.ShopItemData itemData) {
        int stockCount = plugin.getShopManager().getStockCount(shopData);
        if (stockCount == 0) {
            player.sendMessage(ChatColor.RED + "[ショップ] 在庫切れです。");
            return;
        }

        // オーナーが未設定の場合は管理者ショップ
        String owner = shopData.owner;
        boolean isAdminShop = (owner == null || owner.isEmpty());

        int productId = itemData.apiProductId;
        String mcId = player.getName();

        player.closeInventory();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // リアルタイムで信頼モードを取得
            boolean isTrusted = false;
            PointsAPI.ServerStatusResponse statusRes = plugin.getPointsAPI().getServerStatus().join();
            if (statusRes != null && statusRes.success) {
                isTrusted = statusRes.isTrusted;
            }
            final boolean finalIsTrusted = isTrusted;

            // 取引開始
            PointsAPI.TxResponse initRes = plugin.getPointsAPI().initiateTx(mcId, productId, isAdminShop ? null : owner).join();

            if (!initRes.success) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(ChatColor.RED + "[ショップ] 購入開始に失敗しました: " + initRes.message));
                return;
            }

            String txToken = initRes.txToken;

            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.GREEN + "[ショップ] " + ChatColor.WHITE + "購入リクエストを送信しました。");
                if (finalIsTrusted) {
                    // 信頼モード: ダッシュボード承認不要なのでシンプルなメッセージ
                    player.sendMessage(ChatColor.GRAY + "しばらくお待ちください。");
                } else {
                    // 通常モード: ダッシュボードでの承認が必要
                    if (initRes.webUrl != null) {
                        player.sendMessage(ChatColor.GRAY + "ダッシュボードで承認してください: " + ChatColor.AQUA + initRes.webUrl);
                    }
                    player.sendMessage(ChatColor.GRAY + "5分以内に承認しないとキャンセルされます。");
                }
            });

            // 既に pending_seller ならすぐ付与
            if ("pending_seller".equals(initRes.status)) {
                completeTransaction(player, txToken, owner);
                return;
            }

            // ポーリング (5秒間隔、最大60回 = 5分)
            final int[] count = {0};
            final BukkitTask[] taskRef = {null};

            taskRef[0] = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                count[0]++;

                // 5分タイムアウト
                if (count[0] > 60) {
                    taskRef[0].cancel();
                    plugin.getPointsAPI().cancelTx(txToken, isAdminShop ? null : owner);
                    Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(ChatColor.RED + "[ショップ] タイムアウトしました。購入がキャンセルされました。"));
                    return;
                }

                PointsAPI.TxResponse pollRes = plugin.getPointsAPI().getTxStatus(txToken, isAdminShop ? null : owner).join();

                if (!pollRes.success) return;

                switch (pollRes.status != null ? pollRes.status : "") {
                    case "pending_seller":
                        taskRef[0].cancel();
                        completeTransaction(player, txToken, owner);
                        break;
                    case "rejected":
                        taskRef[0].cancel();
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(ChatColor.RED + "[ショップ] 購入が拒否されました。"));
                        break;
                    case "expired":
                        taskRef[0].cancel();
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(ChatColor.RED + "[ショップ] 購入リクエストの有効期限が切れました。"));
                        break;
                    case "completed":
                        taskRef[0].cancel();
                        break;
                    default:
                        break;
                }
            }, 100L, 100L); // 5秒後から5秒間隔
        });
    }

    private void completeTransaction(Player player, String txToken, String owner) {
        boolean isAdminShop = (owner == null || owner.isEmpty());
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack toGive = plugin.getShopManager().consumeStock(shopData);
            if (toGive == null) {
                player.sendMessage(ChatColor.RED + "[ショップ] 在庫が切れていたため付与できませんでした。運営にお問い合わせください。");
                plugin.getPointsAPI().cancelTx(txToken, isAdminShop ? null : owner);
                return;
            }

            // アイテムを付与（インベントリ満杯なら足元にドロップ）
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }

            player.sendMessage(ChatColor.GREEN + "[ショップ] " + ChatColor.WHITE + "購入完了しました！アイテムを付与しました。");

            // 代行ヘッダ付きで決済確定（非同期）—失敗時はログに記録して運営にアラート
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                PointsAPI.TxResponse approveRes = plugin.getPointsAPI().approveTx(txToken, isAdminShop ? null : owner).join();
                if (!approveRes.success) {
                    plugin.getLogger().severe("[Shop] approveTx 失敗: token=" + txToken
                        + " player=" + player.getName()
                        + " owner=" + (isAdminShop ? "server" : owner)
                        + " message=" + approveRes.message);
                    Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(ChatColor.YELLOW + "[ショップ] 決済確定に問題が発生しました。運営にお問い合わせください。"));
                }
            });
        });
    }

    public static String getTitle(String displayName) {
        return TITLE_PREFIX + displayName;
    }
}

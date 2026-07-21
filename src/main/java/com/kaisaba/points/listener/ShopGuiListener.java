package com.kaisaba.points.listener;

import com.kaisaba.points.KaisabaPoints;
import com.kaisaba.points.ShopManager;
import com.kaisaba.points.gui.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class ShopGuiListener implements Listener {

    // IdSetupGui のセッションを保持する（プレイヤー名 → IdSetupGui）
    private final java.util.HashMap<String, IdSetupGui> idSetupSessions = new java.util.HashMap<>();
    // StockGui のセッションを保持する（プレイヤー名 → {shopId_hash, itemSlot}）
    private final java.util.HashMap<String, int[]> stockGuiSessions = new java.util.HashMap<>(); // [shopId_hash, itemSlot] → ショップ名で管理
    // StockGui のショップIDを保持する
    private final java.util.HashMap<String, String> stockGuiShopId = new java.util.HashMap<>();
    // ItemSetupGui のセッション（プレイヤー名 → shopId）
    private final java.util.HashMap<String, String> itemSetupShopId = new java.util.HashMap<>();
    // AdminShopMenu のセッション（プレイヤー名 → shopId）
    private final java.util.HashMap<String, String> adminMenuShopId = new java.util.HashMap<>();
    // DeleteConfirmGui のセッション（プレイヤー名 → shopId）
    private final java.util.HashMap<String, String> deleteConfirmShopId = new java.util.HashMap<>();
    // 購入確認GUI のセッション（プレイヤー名 → {shopId, itemSlot}）
    private final java.util.HashMap<String, String[]> purchaseConfirmSession = new java.util.HashMap<>();

    private final KaisabaPoints plugin;

    public ShopGuiListener(KaisabaPoints plugin) {
        this.plugin = plugin;
    }

    // ========== InventoryClickEvent ==========

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();

        // --- AdminShopMenu ---
        if (title.startsWith(AdminShopMenu.TITLE_PREFIX)) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

            String shopId = adminMenuShopId.get(player.getName());
            if (shopId == null) return;
            ShopManager.ShopData shopData = plugin.getShopManager().getShop(shopId);
            if (shopData == null) return;

            int slot = e.getRawSlot();
            if (slot == 10) {
                // 編集モード切替
                shopData.editMode = !shopData.editMode;
                plugin.getShopManager().saveShops();
                player.closeInventory();
                new AdminShopMenu(plugin, shopData).open(player);
            } else if (slot == 13) {
                // アイテム設定: 編集モードがONの時のみ開ける
                if (!shopData.editMode) {
                    player.sendMessage(ChatColor.RED + "[ショップ] 編集モードをONにしてからアイテム設定を開いてください。");
                    return;
                }
                player.closeInventory();
                itemSetupShopId.put(player.getName(), shopId);
                new ItemSetupGui(plugin, shopData).open(player);
            } else if (slot == 16) {
                // 在庫管理: 直接 StockGui を開く
                player.closeInventory();
                stockGuiShopId.put(player.getName(), shopId);
                new StockGui(plugin, shopData).open(player);
            }
            return;
        }

        // --- ItemSetupGui ---
        if (title.startsWith(ItemSetupGui.TITLE_PREFIX)) {
            int raw = e.getRawSlot();

            // 最下行(45-53): 全てキャンセル
            if (raw >= 45 && raw < 54) {
                e.setCancelled(true);

                if (raw == 52) {
                    // トグルボタン: IDモード切替
                    String shopId = itemSetupShopId.get(player.getName());
                    if (shopId == null) return;
                    ShopManager.ShopData shopData = plugin.getShopManager().getShop(shopId);
                    if (shopData == null) return;
                    boolean newMode = !ItemSetupGui.isIdMode(player);
                    // staticマップを更新してトグルボタンだけ差し替え（GUIは閉じない）
                    ItemSetupGui.setIdMode(player, newMode);
                    e.getInventory().setItem(52, ItemSetupGui.buildToggleButton(newMode));

                } else if (raw == 53) {
                    // 戻るボタン
                    String shopId = itemSetupShopId.get(player.getName());
                    if (shopId == null) return;
                    ShopManager.ShopData shopData = plugin.getShopManager().getShop(shopId);
                    if (shopData == null) return;
                    ItemSetupGui.clearIdMode(player);
                    player.closeInventory();
                    new AdminShopMenu(plugin, shopData).open(player);
                }
                return;
            }

            // プレイヤーインベントリ側(>=54): 操作を通常通り許可
            if (raw >= 54) {
                return;
            }

            // GUI側(0-44)
            if (raw >= 0) {
                String shopId = itemSetupShopId.get(player.getName());
                if (shopId == null) return;
                ShopManager.ShopData shopData = plugin.getShopManager().getShop(shopId);
                if (shopData == null) return;

                boolean idMode = ItemSetupGui.isIdMode(player);

                if (idMode) {
                    // IDモードON: カーソルが空でアイテムをクリック → IdSetupGui を開く
                    ItemStack current = e.getCurrentItem();
                    ItemStack cursor = e.getCursor();
                    boolean cursorEmpty = (cursor == null || cursor.getType() == Material.AIR);

                    if (current != null && current.getType() != Material.AIR && cursorEmpty) {
                        e.setCancelled(true);
                        IdSetupGui idGui = new IdSetupGui(plugin, shopData, raw);
                        idSetupSessions.put(player.getName(), idGui);
                        player.closeInventory();
                        idGui.open(player);
                    }
                    // IDモードONでShiftClickはキャンセル（意図しない配置防止）
                    if (e.isShiftClick()) {
                        e.setCancelled(true);
                    }
                } else {
                    // IDモードOFF
                    // ShiftClickはキャンセル（プレイヤー側 → GUI側の移動防止）
                    if (e.isShiftClick() && raw >= 54) {
                        e.setCancelled(true);
                        return;
                    }

                    ItemStack cursor = e.getCursor();
                    ItemStack current = e.getCurrentItem();
                    boolean cursorEmpty = (cursor == null || cursor.getType() == Material.AIR);

                    if (!cursorEmpty) {
                        // カーソルにアイテムあり → materialId を即時保存してアイテムを返却
                        e.setCancelled(true);
                        String materialId = cursor.getType().name();

                        if (!shopData.items.containsKey(raw)) {
                            shopData.items.put(raw, new ShopManager.ShopItemData());
                        }
                        shopData.items.get(raw).materialId = materialId;
                        plugin.getShopManager().saveShops();

                        // GUIのスロットをアイコンで更新
                        int productId = shopData.items.get(raw).apiProductId;
                        ItemStack icon = ItemSetupGui.buildIconFromMaterialId(materialId, productId);
                        e.getInventory().setItem(raw, icon);

                        // APIへプッシュ
                        if (plugin.getShopApiSync() != null) {
                            plugin.getShopApiSync().pushUpdate(shopData);
                        }

                    } else if (current != null && current.getType() != Material.AIR) {
                        // カーソルが空でアイコンをクリック → そのスロットを削除
                        e.setCancelled(true);
                        shopData.items.remove(raw);
                        plugin.getShopManager().saveShops();
                        e.getInventory().setItem(raw, null);

                        // APIへプッシュ
                        if (plugin.getShopApiSync() != null) {
                            plugin.getShopApiSync().pushUpdate(shopData);
                        }
                    } else {
                        // 空スロットへ空カーソル → 何もしない
                        e.setCancelled(true);
                    }
                }
            }
            return;
        }


        // --- IdSetupGui ---
        if (title.startsWith(IdSetupGui.TITLE_PREFIX)) {
            IdSetupGui idGui = idSetupSessions.get(player.getName());
            if (idGui == null) return;

            int slot = e.getRawSlot();

            // プレイヤーインベントリ側(>=27): ShiftClickのみキャンセル（通常クリックでアイテムを持てるようにする）
            if (slot >= 27) {
                if (e.isShiftClick()) e.setCancelled(true);
                return;
            }

            // GUI側(0-26): 常にキャンセル
            e.setCancelled(true);

            // 最下行(18-26): 戻るボタンのみ処理
            if (slot >= 18 && slot < 27) {
                if (slot == IdSetupGui.BACK_SLOT) {
                    // 戻るボタン: IDを保存してからItemSetupGuiへ
                    IdSetupGui finishedGui = idSetupSessions.remove(player.getName());
                    ShopManager.ShopData shopData = finishedGui.getShopData();
                    int itemSlot = finishedGui.getItemSlot();
                    if (!shopData.items.containsKey(itemSlot)) {
                        shopData.items.put(itemSlot, new ShopManager.ShopItemData());
                    }
                    shopData.items.get(itemSlot).apiProductId = finishedGui.getCurrentId();
                    plugin.getShopManager().saveShops();
                    player.closeInventory();
                    // IDモードONのままItemSetupGuiへ戻る
                    new ItemSetupGui(plugin, shopData).open(player, true);
                }
                return;
            }

            if (slot == IdSetupGui.COMPASS_SLOT) {
                // コンパスをクリック → ID-1
                idGui.decrementId();
                e.getInventory().setItem(IdSetupGui.COMPASS_SLOT, idGui.buildCompass());
            } else if (slot >= 0 && slot < 18) {
                // コンパス以外のスロット(0-17)にアイテムを入れようとしている → ID+1
                // アイテムをGUIには置かせず（キャンセル済み）、ID+1だけ行う
                if (e.getCursor() != null && e.getCursor().getType() != Material.AIR) {
                    idGui.incrementId();
                    e.getInventory().setItem(IdSetupGui.COMPASS_SLOT, idGui.buildCompass());
                }
            }
            return;
        }

        // --- DeleteConfirmGui ---
        if (title.startsWith(DeleteConfirmGui.TITLE_PREFIX)) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

            String shopId = deleteConfirmShopId.get(player.getName());
            if (shopId == null) return;

            int slot = e.getRawSlot();
            if (slot == DeleteConfirmGui.OK_SLOT) {
                // OKボタン: ショップを削除
                deleteConfirmShopId.remove(player.getName());
                ShopManager.ShopData shopData = plugin.getShopManager().getShop(shopId);
                String displayName = (shopData != null) ? shopData.displayName : shopId;
                plugin.getShopManager().deleteShop(shopId);
                player.closeInventory();
                player.sendMessage(org.bukkit.ChatColor.GREEN + "[ショップ] ショップ「" + displayName + "」を削除しました。");
            } else if (slot == DeleteConfirmGui.CANCEL_SLOT) {
                // キャンセルボタン
                deleteConfirmShopId.remove(player.getName());
                player.closeInventory();
                player.sendMessage(org.bukkit.ChatColor.YELLOW + "[ショップ] 削除をキャンセルしました。");
            }
            return;
        }

        // --- StockGui ---
        if (title.startsWith(StockGui.TITLE_PREFIX)) {
            int raw = e.getRawSlot();

            // 最下行(45-53): 全てキャンセル
            if (raw >= 45 && raw < 54) {
                e.setCancelled(true);
                // スロット53: 戻るボタン → AdminShopMenu に戻る
                if (raw == 53) {
                    String shopId = stockGuiShopId.get(player.getName());
                    if (shopId == null) return;
                    ShopManager.ShopData shopData = plugin.getShopManager().getShop(shopId);
                    if (shopData == null) return;
                    player.closeInventory(); // onClose が在庫を保存してくれる
                    new AdminShopMenu(plugin, shopData).open(player);
                }
                return;
            }

            // カーソルにあるアイテム（GUIへドロップ/スワップする候補）
            ItemStack cursor = e.getCursor();
            // プレイヤー側スロットのアイテム（ShiftClickでGUIへ移動する候補）
            ItemStack slotItem = e.getCurrentItem();

            if (raw >= 0 && raw < 45) {
                // GUI側スロット(0-44)へ直接置く操作: カーソルのアイテムをチェック
                if (cursor != null && cursor.getType() != Material.AIR && hasDamage(cursor)) {
                    e.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "[在庫] 耐久値が減っているアイテムは入れられません。");
                    return;
                }
            } else if (raw >= 54) {
                // プレイヤー側スロットからGUIへ移動する操作（ShiftClick含む全操作）
                if (slotItem != null && slotItem.getType() != Material.AIR && hasDamage(slotItem)) {
                    e.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "[在庫] 耐久値が減っているアイテムは入れられません。");
                    return;
                }
            }
            return;
        }


        // --- 購入確認GUI ---
        if (title.startsWith(DeleteConfirmGui.PURCHASE_TITLE_PREFIX)) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

            String[] session = purchaseConfirmSession.get(player.getName());
            if (session == null) return;
            String shopId   = session[0];
            int    itemSlot = Integer.parseInt(session[1]);

            ShopManager.ShopData shopData = plugin.getShopManager().getShop(shopId);
            if (shopData == null) return;

            int slot = e.getRawSlot();
            if (slot == DeleteConfirmGui.OK_SLOT) {
                // OK → 購入フロー本体へ
                purchaseConfirmSession.remove(player.getName());
                ShopManager.ShopItemData itemData = shopData.items.get(itemSlot);
                if (itemData == null) return;
                new PlayerShopGui(plugin, shopData).startPurchase(player, itemData);
            } else if (slot == DeleteConfirmGui.CANCEL_SLOT) {
                // キャンセル
                purchaseConfirmSession.remove(player.getName());
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "[ショップ] 購入をキャンセルしました。");
            }
            return;
        }

        // --- PlayerShopGui ---
        if (title.startsWith(PlayerShopGui.TITLE_PREFIX)) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
            if (e.getRawSlot() >= 54) return;

            // どのショップか探す
            ShopManager.ShopData shopData = null;
            for (ShopManager.ShopData sd : plugin.getShopManager().getAllShops().values()) {
                if (PlayerShopGui.getTitle(sd.displayName).equals(title)) {
                    shopData = sd;
                    break;
                }
            }
            if (shopData == null) return;
            if (shopData.editMode) {
                player.sendMessage(ChatColor.RED + "[ショップ] このショップは現在編集中です。");
                return;
            }

            int clickedSlot = e.getRawSlot();
            ShopManager.ShopItemData itemData = shopData.items.get(clickedSlot);
            if (itemData == null || itemData.materialId == null) return;

            // 在庫チェック
            if (plugin.getShopManager().getStockCount(shopData) == 0) {
                player.sendMessage(ChatColor.RED + "[ショップ] 在庫切れです。");
                return;
            }

            // 購入確認GUIを開く
            // 現在表示中の商品一覧（価格取得用）を非同期で再取得して確認GUIへ
            final ShopManager.ShopData finalShopData = shopData;
            final ShopManager.ShopItemData finalItemData = itemData;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                java.util.List<com.kaisaba.points.PointsAPI.ProductResponse> products;
                if (finalShopData.owner != null && !finalShopData.owner.isEmpty()) {
                    products = plugin.getPointsAPI().getProducts(finalShopData.owner).join();
                } else {
                    products = plugin.getProducts();
                }
                final java.util.List<com.kaisaba.points.PointsAPI.ProductResponse> finalProducts = products;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    purchaseConfirmSession.put(player.getName(),
                        new String[]{finalShopData.id, String.valueOf(clickedSlot)});
                    new PlayerShopGui(plugin, finalShopData)
                        .openPurchaseConfirm(player, finalItemData, finalProducts);
                });
            });
            return;
        }

        // --- ShopListGui ---
        if (ShopListGui.TITLE.equals(title)) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
            if (e.getRawSlot() >= 54) return;

            // スロット番号からショップを探す
            int slot = 0;
            for (Map.Entry<String, ShopManager.ShopData> entry : plugin.getShopManager().getAllShops().entrySet()) {
                ShopManager.ShopData sd = entry.getValue();
                if (sd.editMode) continue;
                if (slot == e.getRawSlot()) {
                    player.closeInventory();
                    new PlayerShopGui(plugin, sd).open(player);
                    return;
                }
                slot++;
            }
        }
    }

    // ========== InventoryDragEvent ==========

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        String title = e.getView().getTitle();

        if (title.startsWith(AdminShopMenu.TITLE_PREFIX)
            || title.startsWith(IdSetupGui.TITLE_PREFIX)
            || title.startsWith(PlayerShopGui.TITLE_PREFIX)
            || ShopListGui.TITLE.equals(title)) {
            e.setCancelled(true);
            return;
        }

        // ItemSetupGui: ドラッグでアイテムを置こうとした場合もキャンセルして即時保存
        if (title.startsWith(ItemSetupGui.TITLE_PREFIX)) {
            Player player = (Player) e.getWhoClicked();
            String shopId = itemSetupShopId.get(player.getName());
            if (shopId == null) {
                e.setCancelled(true);
                return;
            }
            ShopManager.ShopData shopData = plugin.getShopManager().getShop(shopId);
            if (shopData == null) {
                e.setCancelled(true);
                return;
            }
            // GUI側スロット(0-44)にドラッグしようとしている場合はキャンセルし、アイテムを返却
            boolean hasGuiSlot = e.getRawSlots().stream().anyMatch(s -> s < 45);
            if (hasGuiSlot) {
                e.setCancelled(true);
                // ドラッグはカーソルのアイテムを全スロットに分割するため、ここでは
                // 一括ドラッグは不サポートとしてキャンセルのみ行う
                player.sendMessage(ChatColor.YELLOW + "[アイテム設定] ドラッグは使用できません。アイテムをクリックで1スロットに入れてください。");
            }
            return;
        }

        // StockGui: ドラッグで入れようとしたアイテムの耐久値チェック
        if (title.startsWith(StockGui.TITLE_PREFIX)) {
            ItemStack item = e.getOldCursor();
            if (hasDamage(item)) {
                e.setCancelled(true);
                ((Player) e.getWhoClicked()).sendMessage(ChatColor.RED + "[在庫] 耐久値が減っているアイテムは入れられません。");
            }
        }
    }

    // ========== InventoryCloseEvent ==========

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player player = (Player) e.getPlayer();
        String title = e.getView().getTitle();

        // --- ItemSetupGui クローズ → 保存はクリック時に既に行われているため何もしない ---
        if (title.startsWith(ItemSetupGui.TITLE_PREFIX)) {
            // materialId の保存はクリック時に即時行われるため、onClose での保存は不要
            return;
        }

        // --- IdSetupGui クローズ → apiProductId を保存 ---
        if (title.startsWith(IdSetupGui.TITLE_PREFIX)) {
            IdSetupGui idGui = idSetupSessions.remove(player.getName());
            if (idGui == null) return;

            ShopManager.ShopData shopData = idGui.getShopData();
            int slot = idGui.getItemSlot();
            if (!shopData.items.containsKey(slot)) {
                shopData.items.put(slot, new ShopManager.ShopItemData());
            }
            shopData.items.get(slot).apiProductId = idGui.getCurrentId();
            plugin.getShopManager().saveShops();
            return;
        }

        // --- StockGui クローズ → shopData.stockInventory を保存 ---
        if (title.startsWith(StockGui.TITLE_PREFIX)) {
            String shopId = stockGuiShopId.remove(player.getName());
            if (shopId == null) return;

            ShopManager.ShopData shopData = plugin.getShopManager().getShop(shopId);
            if (shopData == null) return;

            Inventory inv = e.getInventory();
            boolean blocked = false;
            for (int i = 0; i < 45; i++) { // 最下行(45-53)は保存しない
                ItemStack stack = inv.getItem(i);
                // 最終防衛ライン: クリックイベントをすり抜けた耐久値低下アイテムをここで弾く
                if (hasDamage(stack)) {
                    player.getInventory().addItem(stack);
                    inv.setItem(i, null);
                    stack = null;
                    blocked = true;
                }
                shopData.stockInventory[i] = stack;
            }
            if (blocked) {
                player.sendMessage(ChatColor.RED + "[在庫] 耐久値が減っているアイテムは在庫に登録できません。返却しました。");
            }
            plugin.getShopManager().saveShops();

            // 在庫変動をAPIへプッシュ
            if (plugin.getShopApiSync() != null) {
                plugin.getShopApiSync().pushUpdate(shopData);
            }
        }
    }

    // ========== ユーティリティ ==========

    private boolean hasDamage(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable) {
            return ((Damageable) meta).getDamage() > 0;
        }
        return false;
    }

    // ========== PlayerQuitEvent ==========

    /**
     * プレイヤーが切断したときにセッションをクリーンアップする。
     * セッションが残存するとメモリリークや意図しない操作継続の原因になる。
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String name = e.getPlayer().getName();
        idSetupSessions.remove(name);
        stockGuiSessions.remove(name);
        stockGuiShopId.remove(name);
        itemSetupShopId.remove(name);
        adminMenuShopId.remove(name);
        deleteConfirmShopId.remove(name);
        purchaseConfirmSession.remove(name);
        ItemSetupGui.clearIdMode(e.getPlayer());
    }

    // --- セッション登録用メソッド ---

    public void registerAdminMenu(Player player, String shopId) {
        adminMenuShopId.put(player.getName(), shopId);
    }

    public void registerItemSetup(Player player, String shopId) {
        itemSetupShopId.put(player.getName(), shopId);
    }

    public void registerDeleteConfirm(Player player, String shopId) {
        deleteConfirmShopId.put(player.getName(), shopId);
    }
}

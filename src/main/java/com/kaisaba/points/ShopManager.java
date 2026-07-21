package com.kaisaba.points;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopManager {

    private final KaisabaPoints plugin;
    private final File shopsFile;
    private YamlConfiguration shopsConfig;

    /** ShopApiSync への参照 */
    private ShopApiSync apiSync = null;

    // インメモリのショップデータ
    // LinkedHashMap で挿入順を保持し、ShopListGui とクリックイベントの順序を一致させる
    private final Map<String, ShopData> shops = new LinkedHashMap<>();

    public ShopManager(KaisabaPoints plugin) {
        this.plugin = plugin;
        this.shopsFile = new File(plugin.getDataFolder(), "shops.yml");
        loadShops();
    }

    // ========== YAML ロード/セーブ ==========

    public void loadShops() {
        shops.clear();
        if (!shopsFile.exists()) {
            shopsConfig = new YamlConfiguration();
            return;
        }
        shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
        ConfigurationSection root = shopsConfig.getConfigurationSection("shops");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;

            ShopData data = new ShopData();
            data.id = id;
            data.displayName = sec.getString("display-name", id);
            data.editMode = sec.getBoolean("edit-mode", false);
            data.owner = sec.getString("owner", null);

            // ショップ全体共有の在庫をロード
            List<?> shopStockList = sec.getList("stock-inventory");
            if (shopStockList != null) {
                data.stockInventory = new ItemStack[45];
                for (int i = 0; i < Math.min(shopStockList.size(), 45); i++) {
                    Object obj = shopStockList.get(i);
                    if (obj instanceof ItemStack) {
                        data.stockInventory[i] = (ItemStack) obj;
                    }
                }
            } else {
                data.stockInventory = new ItemStack[45];
            }

            ConfigurationSection itemsSec = sec.getConfigurationSection("items");
            if (itemsSec != null) {
                for (String slotKey : itemsSec.getKeys(false)) {
                    ConfigurationSection itemSec = itemsSec.getConfigurationSection(slotKey);
                    if (itemSec == null) continue;
                    int slot = Integer.parseInt(slotKey);

                    ShopItemData itemData = new ShopItemData();
                    itemData.apiProductId = itemSec.getInt("api-product-id", 0);
                    itemData.materialId = itemSec.getString("material-id", null);

                    data.items.put(slot, itemData);
                }
            }

            shops.put(id, data);
        }
    }

    public void saveShops() {
        shopsConfig = new YamlConfiguration();
        for (ShopData data : shops.values()) {
            String base = "shops." + data.id;
            shopsConfig.set(base + ".display-name", data.displayName);
            shopsConfig.set(base + ".edit-mode", data.editMode);
            shopsConfig.set(base + ".owner", data.owner);

            // ショップ全体共有の在庫を保存
            List<ItemStack> shopStockList = new ArrayList<>();
            for (ItemStack stack : data.stockInventory) {
                shopStockList.add(stack);
            }
            shopsConfig.set(base + ".stock-inventory", shopStockList);

            for (Map.Entry<Integer, ShopItemData> entry : data.items.entrySet()) {
                String itemBase = base + ".items." + entry.getKey();
                ShopItemData item = entry.getValue();
                shopsConfig.set(itemBase + ".api-product-id", item.apiProductId);
                shopsConfig.set(itemBase + ".material-id", item.materialId);
            }
        }
        try {
            shopsConfig.save(shopsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("shops.yml の保存に失敗しました: " + e.getMessage());
        }
    }

    // ========== CRUD ==========

    public ShopData getShop(String id) {
        return shops.get(id);
    }

    public Map<String, ShopData> getAllShops() {
        return shops;
    }

    public void createShop(String id, String displayName) {
        createShop(id, displayName, null);
    }

    public void createShop(String id, String displayName, String ownerMcId) {
        ShopData data = new ShopData();
        data.id = id;
        data.displayName = displayName;
        data.owner = ownerMcId;
        shops.put(id, data);
        saveShops();
        // APIサーバーへプッシュ
        if (apiSync != null) apiSync.pushCreate(data);
    }

    /**
     * リモートからのポーリング経由でショップを作成する（APIへ再プッシュしない）。
     */
    public void createShopLocal(String id, String displayName) {
        ShopData data = new ShopData();
        data.id = id;
        data.displayName = displayName;
        shops.put(id, data);
        // saveShops()は呼び出し元でまとめて行う
    }

    public void deleteShop(String id) {
        shops.remove(id);
        saveShops();
        // APIサーバーへプッシュ
        if (apiSync != null) apiSync.pushDelete(id);
    }

    /** ShopApiSync をセットする（KaisabaPoints#onEnable から呼び出す）。 */
    public void setApiSync(ShopApiSync apiSync) {
        this.apiSync = apiSync;
    }

    // ========== 在庫ユーティリティ ==========

    /** ショップの共有在庫の非-null要素数を返す */
    public int getStockCount(ShopData shop) {
        int count = 0;
        for (ItemStack stack : shop.stockInventory) {
            if (stack != null) count += stack.getAmount();
        }
        return count;
    }

    /**
     * 共有在庫から先頭の非-null要素を、1つ取り出して返す。
     * 取り出した位置は null になる。
     * synchronized により複数プレイヤーの同時購入によるRace Conditionを防ぐ。
     */
    public synchronized ItemStack consumeStock(ShopData shop) {
        for (int i = 0; i < shop.stockInventory.length; i++) {
            if (shop.stockInventory[i] != null) {
                ItemStack result = shop.stockInventory[i];
                if (result.getAmount() > 1) {
                    result = result.clone();
                    result.setAmount(1);
                    shop.stockInventory[i].setAmount(shop.stockInventory[i].getAmount() - 1);
                } else {
                    shop.stockInventory[i] = null;
                }
                saveShops();
                return result;
            }
        }
        return null;
    }

    // ========== データクラス ==========

    public static class ShopData {
        public String id;
        public String displayName;
        public boolean editMode = false;
        public String owner = null;
        public Map<Integer, ShopItemData> items = new LinkedHashMap<>();
        /** ショップ全体共有の在庫（45スロット） */
        public ItemStack[] stockInventory = new ItemStack[45];
    }

    public static class ShopItemData {
        public int apiProductId = 0;
        /** アイテムの見た目を表す Material 名（例: "DIAMOND_SWORD"）。ItemStack は保持しない。 */
        public String materialId = null;
    }
}

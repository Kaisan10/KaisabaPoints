package com.kaisaba.points;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ウェブAPIサーバーとショップデータを同期するクラス。
 *
 * 同期方針:
 *   Minecraftサーバー起動時・定期ポーリング（デフォルト60秒）でAPIサーバーからショップ設定を取得し、
 *   ローカルの shops.yml へマージ
 *   ゲーム内でショップを作成・削除・編集した際にAPIサーバーへプッシュ
 *   在庫（stockInventory）はMinecraft側でのみ管理し、在庫数（stock_count）はAPIサーバーへ通知する
 *
 * APIサーバー側で実装が必要なエンドポイント（詳細は api-spec.md を参照）:
 *   GET  /api/server/shop/list
 *   POST /api/server/shop
 *   PATCH /api/server/shop/{id}
 *   DELETE /api/server/shop/{id}
 */
public class ShopApiSync {

    private final KaisabaPoints plugin;
    private final String apiUrl;
    private final String apiKey;
    private final int timeoutMs;
    private final Logger logger;

    /** ポーリングタスクのID（キャンセル用） */
    private int taskId = -1;

    public ShopApiSync(KaisabaPoints plugin, String apiUrl, String apiKey, int timeoutSeconds) {
        this.plugin = plugin;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.timeoutMs = timeoutSeconds * 1000;
        this.logger = plugin.getLogger();
    }

    // ========== ポーリング ==========

    /**
     * 定期ポーリングを開始する。
     * @param intervalSeconds ポーリング間隔（秒）
     */
    public void startPolling(int intervalSeconds) {
        stopPolling();
        long ticks = intervalSeconds * 20L;
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollFromApi, ticks, ticks).getTaskId();
        logger.info("[ShopSync] ポーリング開始: " + intervalSeconds + "秒間隔");
    }

    /** ポーリングを停止する。 */
    public void stopPolling() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    // ========== API → ローカル同期（ポーリング） ==========

    /**
     * APIサーバーからショップ一覧を取得してローカルにマージする。
     * 非同期スレッドから呼ばれる。
     */
    public void pollFromApi() {
        try {
            URL url = URI.create(apiUrl + "/api/server/shop/list").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-API-Key", apiKey);
            conn.setRequestProperty("User-Agent", "KaisabaPoints/Minecraft");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);

            int status = conn.getResponseCode();
            String body = readResponse(conn);

            if (status == 404) {
                // エンドポイント未実装の場合は静かにスキップ
                return;
            }
            if (status != 200) {
                logger.warning("[ShopSync] ショップ一覧取得失敗 (HTTP " + status + "): " + body);
                return;
            }

            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (!json.has("success") || !json.get("success").getAsBoolean()) {
                String msg = json.has("message") ? json.get("message").getAsString() : "unknown error";
                logger.warning("[ShopSync] APIエラー: " + msg);
                return;
            }

            JsonArray data = json.getAsJsonArray("data");
            applyRemoteShops(data);

        } catch (Exception e) {
            logger.warning("[ShopSync] ポーリングエラー: " + e.getMessage());
        }
    }

    /**
     * リモートのショップ配列をローカルへ適用する（メインスレッドで実行）。
     */
    private void applyRemoteShops(JsonArray data) {
        // メインスレッドで適用
        Bukkit.getScheduler().runTask(plugin, () -> {
            ShopManager manager = plugin.getShopManager();
            boolean changed = false;

            for (JsonElement elem : data) {
                JsonObject shopJson = elem.getAsJsonObject();
                String id = shopJson.has("id") ? shopJson.get("id").getAsString() : null;
                if (id == null || id.isEmpty()) continue;

                String displayName = shopJson.has("display_name")
                        ? shopJson.get("display_name").getAsString() : id;
                boolean editMode = shopJson.has("edit_mode")
                        && shopJson.get("edit_mode").getAsBoolean();

                ShopManager.ShopData existing = manager.getShop(id);

                if (existing == null) {
                    // 新規ショップをリモートから追加
                    manager.createShopLocal(id, displayName);
                    existing = manager.getShop(id);
                    changed = true;
                } else {
                    // 既存ショップのメタデータを更新
                    if (!existing.displayName.equals(displayName)) {
                        existing.displayName = displayName;
                        changed = true;
                    }
                    if (existing.editMode != editMode) {
                        existing.editMode = editMode;
                        changed = true;
                    }
                }

                // アイテム設定（apiProductId, materialId）をマージ
                if (shopJson.has("items") && existing != null) {
                    JsonObject itemsJson = shopJson.getAsJsonObject("items");
                    for (Map.Entry<String, JsonElement> entry : itemsJson.entrySet()) {
                        try {
                            int slot = Integer.parseInt(entry.getKey());
                            JsonObject itemObj = entry.getValue().getAsJsonObject();
                            int apiProductId = itemObj.has("api_product_id")
                                    ? itemObj.get("api_product_id").getAsInt() : 0;
                            String materialId = itemObj.has("material_id")
                                    ? itemObj.get("material_id").getAsString() : null;

                            if (!existing.items.containsKey(slot)) {
                                existing.items.put(slot, new ShopManager.ShopItemData());
                            }
                            if (existing.items.get(slot).apiProductId != apiProductId) {
                                existing.items.get(slot).apiProductId = apiProductId;
                                changed = true;
                            }
                            // materialId の更新
                            String currentMaterialId = existing.items.get(slot).materialId;
                            if (materialId != null && !materialId.equals(currentMaterialId)) {
                                existing.items.get(slot).materialId = materialId;
                                changed = true;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            if (changed) {
                manager.saveShops();
                logger.info("[ShopSync] リモートからの変更を適用しました");
            }
        });
    }

    // ========== ローカル → API プッシュ ==========

    /**
     * ショップ作成をAPIサーバーにプッシュする（非同期）。
     */
    public void pushCreate(ShopManager.ShopData shopData) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = URI.create(apiUrl + "/api/server/shop").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "KaisabaPoints/Minecraft");
                conn.setDoOutput(true);
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);

                JsonObject body = buildShopJson(shopData);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();
                if (status != 200 && status != 201) {
                    String respBody = readResponse(conn);
                    logger.warning("[ShopSync] ショップ作成プッシュ失敗 (HTTP " + status + "): " + respBody);
                }
            } catch (Exception e) {
                logger.warning("[ShopSync] ショップ作成プッシュエラー: " + e.getMessage());
            }
        });
    }

    /**
     * ショップ更新をAPIサーバーにプッシュする（非同期）。
     */
    public void pushUpdate(ShopManager.ShopData shopData) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = URI.create(apiUrl + "/api/server/shop/" + shopData.id).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PATCH");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "KaisabaPoints/Minecraft");
                conn.setDoOutput(true);
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);

                JsonObject body = buildShopJson(shopData);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();
                if (status != 200) {
                    String respBody = readResponse(conn);
                    logger.warning("[ShopSync] ショップ更新プッシュ失敗 (HTTP " + status + "): " + respBody);
                }
            } catch (Exception e) {
                logger.warning("[ShopSync] ショップ更新プッシュエラー: " + e.getMessage());
            }
        });
    }

    /**
     * ショップ削除をAPIサーバーにプッシュする（非同期）。
     */
    public void pushDelete(String shopId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = URI.create(apiUrl + "/api/server/shop/" + shopId).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("User-Agent", "KaisabaPoints/Minecraft");
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);

                int status = conn.getResponseCode();
                if (status != 200 && status != 204) {
                    String respBody = readResponse(conn);
                    logger.warning("[ShopSync] ショップ削除プッシュ失敗 (HTTP " + status + "): " + respBody);
                }
            } catch (Exception e) {
                logger.warning("[ShopSync] ショップ削除プッシュエラー: " + e.getMessage());
            }
        });
    }

    // ========== ユーティリティ ==========

    /** ShopData を API 送信用 JSON に変換する。在庫数（stock_count）と material_id を含む。 */
    private JsonObject buildShopJson(ShopManager.ShopData shopData) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", shopData.id);
        obj.addProperty("display_name", shopData.displayName);
        obj.addProperty("edit_mode", shopData.editMode);
        obj.addProperty("stock_count", plugin.getShopManager().getStockCount(shopData));

        JsonObject items = new JsonObject();
        for (Map.Entry<Integer, ShopManager.ShopItemData> entry : shopData.items.entrySet()) {
            JsonObject item = new JsonObject();
            item.addProperty("api_product_id", entry.getValue().apiProductId);
            if (entry.getValue().materialId != null) {
                item.addProperty("material_id", entry.getValue().materialId);
            }
            items.add(String.valueOf(entry.getKey()), item);
        }
        obj.add("items", items);

        return obj;
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        InputStream stream;
        try {
            stream = conn.getInputStream();
        } catch (Exception e) {
            stream = conn.getErrorStream();
        }
        if (stream == null) return "";
        BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) sb.append(line);
        in.close();
        return sb.toString();
    }
}

package com.kaisaba.points;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PointsPlaceholder extends PlaceholderExpansion {

    private final KaisabaPoints plugin;
    private final Map<String, CachedPoints> pointsCache = new ConcurrentHashMap<>();
    private final long cacheDuration;
    private long lastLogTime = 0;
    private static final long LOG_INTERVAL = 5000; // 5秒に1回のみログ

    public PointsPlaceholder(KaisabaPoints plugin, long cacheDuration) {
        this.plugin = plugin;
        this.cacheDuration = cacheDuration;
    }

    @Override
    public String getIdentifier() {
        return "kg";
    }

    @Override
    public String getAuthor() {
        return "bac0n";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        // プレースホルダーパラメータの処理
        if (params == null || (!params.equals("points") && !params.isEmpty())) {
            return null; // 不明なプレースホルダーはnullを返す
        }

        if (player == null) {
            return "-";
        }

        String playerName = player.getName();
        if (playerName == null) {
            return "-";
        }

        // キャッシュチェック
        CachedPoints cached = pointsCache.get(playerName);
        long currentTime = System.currentTimeMillis();

        if (cached != null && (currentTime - cached.timestamp) < cacheDuration) {
            return cached.points != null ? String.valueOf(cached.points) : "-";
        }

        // キャッシュが期限切れまたは存在しない場合、非同期で取得
        if (cached == null || (currentTime - cached.timestamp) >= cacheDuration) {
            // 非同期でポイント取得
            plugin.getPointsAPI().getPlayerInfo(playerName).thenAccept(response -> {
                if (response.success) {
                    pointsCache.put(playerName, new CachedPoints(response.points, currentTime));
                } else {
                    // エラーの場合は"-"を設定
                    pointsCache.put(playerName, new CachedPoints(null, currentTime));
                    // ログ出力を抑制（一定間隔のみ）
                    if (currentTime - lastLogTime > LOG_INTERVAL) {
                        lastLogTime = currentTime;
                        plugin.getLogger().warning("ポイント取得エラー (" + playerName + "): " + response.message);
                    }
                }
            });
        }

        // 既存のキャッシュがあれば返す（更新中）
        if (cached != null) {
            return cached.points != null ? String.valueOf(cached.points) : "-";
        }

        // 初回取得の場合は一時的に"-"を返す
        return "-";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        return onRequest(player, params);
    }

    // キャッシュの手動クリア（設定変更時などに使用）
    public void clearCache() {
        pointsCache.clear();
    }

    // 特定プレイヤーのキャッシュをクリア
    public void clearCache(String playerName) {
        pointsCache.remove(playerName);
    }

    // キャッシュされたポイント情報を保持する内部クラス
    private static class CachedPoints {
        final Integer points;
        final long timestamp;

        CachedPoints(Integer points, long timestamp) {
            this.points = points;
            this.timestamp = timestamp;
        }
    }
}


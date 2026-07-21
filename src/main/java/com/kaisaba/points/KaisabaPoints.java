package com.kaisaba.points;

import com.kaisaba.points.listener.ShopGuiListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class KaisabaPoints extends JavaPlugin {

    private PointsAPI pointsAPI;
    private PointsPlaceholder placeholder;
    private ShopManager shopManager;
    private ShopApiSync shopApiSync;

    /** onEnable 後にキャッシュされる商品一覧 */
    private List<PointsAPI.ProductResponse> cachedProducts = new ArrayList<>();


    @Override
    public void onEnable() {
        // 設定ファイルを保存（初回のみ）→ 欠落キーを自動補完
        saveDefaultConfig();
        migrateConfig();

        // API初期化（新config キーと timeout を使用）
        String apiUrl  = getConfig().getString("points-system.api-url", "https://points.bac0n.f5.si");
        String apiKey  = getConfig().getString("points-system.api-key", "");
        int timeoutSec = getConfig().getInt("points-system.timeout", 10);
        pointsAPI = new PointsAPI(apiUrl, apiKey, timeoutSec);

        // ShopManager 初期化
        shopManager = new ShopManager(this);

        // ShopApiSync 初期化（案B: 既存APIサーバー経由）
        boolean shopSyncEnabled = getConfig().getBoolean("shop-sync.enabled", false);
        if (shopSyncEnabled) {
            int pollInterval = getConfig().getInt("shop-sync.poll-interval", 60);
            shopApiSync = new ShopApiSync(this, apiUrl, apiKey, timeoutSec);
            shopManager.setApiSync(shopApiSync);
            // 起動時に一度即時反映
            Bukkit.getScheduler().runTaskAsynchronously(this, shopApiSync::pollFromApi);
            shopApiSync.startPolling(pollInterval);
            getLogger().info("[ShopSync] 有効: ポーリング間隔 " + pollInterval + "秒");
        } else {
            getLogger().info("[ShopSync] 無効（shop-sync.enabled: false）");
        }

        // イベントリスナー登録
        ShopGuiListener shopGuiListener = new ShopGuiListener(this);
        getServer().getPluginManager().registerEvents(shopGuiListener, this);

        // コマンド登録
        getCommand("points").setExecutor(new PointsCommand(this, pointsAPI));
        ShopCommand shopCommand = new ShopCommand(this, shopGuiListener);
        getCommand("shop").setExecutor(shopCommand);
        getCommand("shop").setTabCompleter(shopCommand);

        // PlaceholderAPI登録
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            long cacheDuration = getConfig().getLong("placeholder.cache-duration", 5000); // デフォルト5秒
            placeholder = new PointsPlaceholder(this, cacheDuration);

            if (placeholder.register()) {
                getLogger().info("PlaceholderAPI連携成功");
                getLogger().info("使用例: %kg_points%");
            } else {
                getLogger().warning("PlaceholderAPI連携失敗");
            }
        } else {
            getLogger().info("PlaceholderAPIが見つかりません。スキップします。");
        }

        // 商品一覧をプラグイン起動時に非同期キャッシュ
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            pointsAPI.getProducts().thenAccept(products -> {
                cachedProducts = products;
                if (products.isEmpty()) {
                    getLogger().warning("商品一覧の取得に失敗したか、商品が登録されていません。");
                } else {
                    getLogger().info("商品一覧をキャッシュしました: " + products.size() + " 件");
                }
            });
        });

        getLogger().info("かい鯖グループポイントプラグイン起動");
    }

    @Override
    public void onDisable() {
        if (placeholder != null) {
            placeholder.clearCache();
        }
        if (shopApiSync != null) {
            shopApiSync.stopPolling();
        }
        getLogger().info("かい鯖グループポイントプラグイン停止");
    }

    public PointsAPI getPointsAPI() {
        return pointsAPI;
    }

    public PointsPlaceholder getPlaceholder() {
        return placeholder;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public ShopApiSync getShopApiSync() {
        return shopApiSync;
    }

    /**
     * config.yml を再読み込みし、PointsAPI と Placeholder を再初期化する。
     */
    public void reloadPlugin() {
        // config.yml を再読み込み
        reloadConfig();

        // PointsAPI を新しい設定で再初期化
        String apiUrl  = getConfig().getString("points-system.api-url", "https://points.bac0n.f5.si");
        String apiKey  = getConfig().getString("points-system.api-key", "");
        int timeoutSec = getConfig().getInt("points-system.timeout", 10);
        pointsAPI = new PointsAPI(apiUrl, apiKey, timeoutSec);

        // コマンドエグゼキュータを新しい pointsAPI で更新
        getCommand("points").setExecutor(new PointsCommand(this, pointsAPI));

        // Placeholder を再初期化
        if (placeholder != null) {
            placeholder.clearCache();
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            long cacheDuration = getConfig().getLong("placeholder.cache-duration", 5000);
            placeholder = new PointsPlaceholder(this, cacheDuration);
            placeholder.register();
        }

        // 商品一覧を再キャッシュ
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            pointsAPI.getProducts().thenAccept(products -> {
                cachedProducts = products;
                getLogger().info("商品一覧を再キャッシュしました: " + products.size() + " 件");
            });
        });

        getLogger().info("設定を再読み込みしました。API URL: " + apiUrl);
    }

    /**
     * キャッシュされた商品一覧を返す。
     * まだロード中の場合は空リストが返る。
     */
    public List<PointsAPI.ProductResponse> getProducts() {
        return cachedProducts;
    }

    /**
     * プレイヤーが /shop create で個人ショップを作れるかどうか。
     * config.yml の shop.player-create-enabled に対応する。
     */
    public boolean isShopPlayerCreateEnabled() {
        return getConfig().getBoolean("shop.player-create-enabled", true);
    }

    /**
     * config.yml のマイグレーション処理。
     * jar 内の default config に存在するキーが現行 config.yml に無い場合のみ追記する。
     * バージョンアップで新設されたキーも自動補完されるため、手動編集不要。
     */
    private void migrateConfig() {
        // jar 内のデフォルト config を読み込む
        InputStream defaultStream = getResource("config.yml");
        if (defaultStream == null) return;

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
            new InputStreamReader(defaultStream, StandardCharsets.UTF_8));

        boolean updated = false;
        for (String key : defaultConfig.getKeys(true)) {
            // セクション（マップ）自体はスキップし、末端の値だけ補完する
            if (defaultConfig.isConfigurationSection(key)) continue;

            if (!getConfig().contains(key)) {
                getConfig().set(key, defaultConfig.get(key));
                updated = true;
                getLogger().info("[Config] 新規キーを追加しました: " + key
                    + " = " + defaultConfig.get(key));
            }
        }

        if (updated) {
            saveConfig();
            getLogger().info("[Config] config.yml を最新バージョンにマイグレーションしました。");
        }
    }
}
package com.kaisaba.points;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PointsAPI {

    private final String apiUrl;
    private final String apiKey;
    private final int timeoutMs;
    private final Logger logger;
    private final ConcurrentHashMap<String, Long> lastErrorLog = new ConcurrentHashMap<>();
    private static final long ERROR_LOG_COOLDOWN = 10000; // 10秒間同じエラーはログしない

    public PointsAPI(String apiUrl, String apiKey, int timeoutSeconds) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.timeoutMs = timeoutSeconds * 1000;
        this.logger = java.util.logging.Logger.getLogger("KaisabaPoints");
    }

    // エラーログを抑制する（短時間の連続エラーを防ぐ）
    private void logErrorOnce(String errorType, String message) {
        long now = System.currentTimeMillis();
        Long lastTime = lastErrorLog.get(errorType);

        if (lastTime == null || now - lastTime > ERROR_LOG_COOLDOWN) {
            logger.warning(message);
            lastErrorLog.put(errorType, now);
        }
    }

    // HTTPレスポンスボディを読み込む（成功・エラー両対応）
    private String readResponse(HttpURLConnection conn) throws Exception {
        InputStream stream;
        try {
            stream = conn.getInputStream();
        } catch (Exception e) {
            // エラー時は getErrorStream() にフォールバック
            stream = conn.getErrorStream();
        }
        if (stream == null) {
            return "{\"success\":false,\"message\":\"レスポンスなし\"}";
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();
        return response.toString();
    }

    // HTTPステータスコードに応じたエラーメッセージを返す
    private String httpStatusMessage(int status) {
        switch (status) {
            case 400: return "リクエストが無効です (400)";
            case 401: return "APIキーが無効です。設定を確認してください (401)";
            case 402: return "ポイントが不足しています (402)";
            case 409: return "すでに処理済みの取引です (409)";
            case 429: return "リクエストが多すぎます。しばらく待ってから再試行してください (429)";
            case 500: return "サーバーエラーが発生しました。運営に報告してください (500)";
            default:  return "HTTPエラー: " + status;
        }
    }

    // ========== プレイヤー情報取得 ==========

    /**
     * プレイヤーの残高・情報を取得
     * GET /api/server/player/:mc_id
     */
    public CompletableFuture<PlayerResponse> getPlayerInfo(String mcId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(apiUrl + "/api/server/player/" + mcId).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("User-Agent", "KaisabaPoints/Minecraft");
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);

                int status = conn.getResponseCode();
                String body = readResponse(conn);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                if (status == 200 && json.has("success") && json.get("success").getAsBoolean()) {
                    JsonObject data = json.getAsJsonObject("data");
                    int points = data.has("points") ? data.get("points").getAsInt() : 0;
                    String id = data.has("mc_id") ? data.get("mc_id").getAsString() : mcId;
                    return new PlayerResponse(true, id, points, null);
                } else {
                    String msg = json.has("message") ? json.get("message").getAsString()
                               : httpStatusMessage(status);
                    return new PlayerResponse(false, null, 0, msg);
                }
            } catch (Exception e) {
                String error = "プレイヤー情報取得エラー: " + e.getMessage();
                logErrorOnce("get_player_error", error);
                return new PlayerResponse(false, null, 0, error);
            }
        });
    }

    // ========== 商品一覧取得 ==========

    /**
     * 商品一覧を取得
     * GET /api/server/products
     */
    public CompletableFuture<List<ProductResponse>> getProducts() {
        return getProducts(null);
    }

    /**
     * 商品一覧を取得（代行操作対応）
     * delegateMcId が null でない場合は x-delegate-mc-id ヘッダを付与し、
     * 指定プレイヤーのサービスアカウントの商品一覧を取得する。
     */
    public CompletableFuture<List<ProductResponse>> getProducts(String delegateMcId) {
        return CompletableFuture.supplyAsync(() -> {
            List<ProductResponse> result = new ArrayList<>();
            try {
                URL url = URI.create(apiUrl + "/api/server/products").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("User-Agent", "KaisabaPoints/Minecraft");
                if (delegateMcId != null && !delegateMcId.isEmpty()) {
                    conn.setRequestProperty("x-delegate-mc-id", delegateMcId);
                }
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);

                int status = conn.getResponseCode();
                String body = readResponse(conn);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                if (status == 200 && json.has("success") && json.get("success").getAsBoolean()) {
                    JsonArray data = json.getAsJsonArray("data");
                    for (int i = 0; i < data.size(); i++) {
                        JsonObject item = data.get(i).getAsJsonObject();
                        int id    = item.has("id")    ? item.get("id").getAsInt()          : 0;
                        String name = item.has("name") ? item.get("name").getAsString()    : "不明";
                        int price = item.has("price") ? item.get("price").getAsInt()       : 0;
                        result.add(new ProductResponse(id, name, price));
                    }
                } else {
                    String msg = json.has("message") ? json.get("message").getAsString()
                               : httpStatusMessage(status);
                    logErrorOnce("get_products_error", "商品一覧取得失敗: " + msg);
                }
            } catch (Exception e) {
                logErrorOnce("get_products_exception", "商品一覧取得エラー: " + e.getMessage());
            }
            return result;
        });
    }

    // ========== 取引フロー ==========

    /**
     * 取引を開始する
     * POST /api/server/tx/initiate
     * Body: { "mc_id": "...", "product_id": 1 }
     * Response: { tx_token, amount, web_url }
     */
    public CompletableFuture<TxResponse> initiateTx(String mcId, int productId) {
        return initiateTx(mcId, productId, null);
    }

    /**
     * 取引を開始する（代行操作対応）
     * delegateMcId が null でない場合は x-delegate-mc-id ヘッダを付与し、
     * 指定プレイヤーのサービスアカウントを売り手として取引を開始する。
     */
    public CompletableFuture<TxResponse> initiateTx(String mcId, int productId, String delegateMcId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(apiUrl + "/api/server/tx/initiate").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "KaisabaPoints/Minecraft");
                if (delegateMcId != null && !delegateMcId.isEmpty()) {
                    conn.setRequestProperty("x-delegate-mc-id", delegateMcId);
                }
                conn.setDoOutput(true);
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);

                JsonObject body = new JsonObject();
                body.addProperty("mc_id", mcId);
                body.addProperty("product_id", productId);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input);
                }

                int status = conn.getResponseCode();
                String responseBody = readResponse(conn);
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                if ((status == 200 || status == 201) && json.has("success") && json.get("success").getAsBoolean()) {
                    JsonObject data = json.getAsJsonObject("data");
                    String txToken = data.has("tx_token") ? data.get("tx_token").getAsString() : null;
                    int amount     = data.has("amount")   ? data.get("amount").getAsInt()      : 0;
                    String webUrl  = data.has("web_url")  ? data.get("web_url").getAsString()  : null;
                    String txStatus = data.has("status") ? data.get("status").getAsString() : "pending_buyer";
                    return new TxResponse(true, txToken, amount, webUrl, txStatus, null);
                } else {
                    String msg = json.has("message") ? json.get("message").getAsString()
                               : httpStatusMessage(status);
                    // 403 は代行未許可を示す
                    if (status == 403) {
                        msg = "ショップオーナーがこのサーバーでの操作許可をオンにしていません。ショップオーナーにお問い合わせください。";
                    }
                    return new TxResponse(false, null, 0, null, null, msg);
                }
            } catch (Exception e) {
                String error = "取引開始エラー: " + e.getMessage();
                logErrorOnce("initiate_tx_error", error);
                return new TxResponse(false, null, 0, null, null, error);
            }
        });
    }

    /**
     * 取引の状態を取得（ポーリング用）
     * GET /api/server/tx/:tx_token
     */
    public CompletableFuture<TxResponse> getTxStatus(String txToken) {
        return getTxStatus(txToken, null);
    }

    /** 取引状態を取得（代行操作対応） */
    public CompletableFuture<TxResponse> getTxStatus(String txToken, String delegateMcId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(apiUrl + "/api/server/tx/" + txToken).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("User-Agent", "KaisabaPoints/Minecraft");
                if (delegateMcId != null && !delegateMcId.isEmpty()) {
                    conn.setRequestProperty("x-delegate-mc-id", delegateMcId);
                }
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);

                int status = conn.getResponseCode();
                String responseBody = readResponse(conn);
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                if (status == 200 && json.has("success") && json.get("success").getAsBoolean()) {
                    JsonObject data = json.getAsJsonObject("data");
                    String txStatus = data.has("status") ? data.get("status").getAsString() : "unknown";
                    return new TxResponse(true, txToken, 0, null, txStatus, null);
                } else {
                    String msg = json.has("message") ? json.get("message").getAsString()
                               : httpStatusMessage(status);
                    return new TxResponse(false, txToken, 0, null, null, msg);
                }
            } catch (Exception e) {
                String error = "取引状態取得エラー: " + e.getMessage();
                logErrorOnce("get_tx_status_error", error);
                return new TxResponse(false, txToken, 0, null, null, error);
            }
        });
    }

    /**
     * 取引を確定させる（アイテム付与後に必ず呼ぶこと）
     * POST /api/server/tx/:tx_token/approve
     */
    public CompletableFuture<TxResponse> approveTx(String txToken) {
        return approveTx(txToken, null);
    }

    /** 取引を確定させる（代行操作対応） */
    public CompletableFuture<TxResponse> approveTx(String txToken, String delegateMcId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(apiUrl + "/api/server/tx/" + txToken + "/approve").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "KaisabaPoints/Minecraft");
                if (delegateMcId != null && !delegateMcId.isEmpty()) {
                    conn.setRequestProperty("x-delegate-mc-id", delegateMcId);
                }
                conn.setDoOutput(true);
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);

                // ボディは空でOK
                try (OutputStream os = conn.getOutputStream()) {
                    os.write("{}".getBytes(StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();
                String responseBody = readResponse(conn);
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                if ((status == 200 || status == 201) && json.has("success") && json.get("success").getAsBoolean()) {
                    return new TxResponse(true, txToken, 0, null, "completed", null);
                } else {
                    // 409 = すでに承認済み → 問題なしとして扱う
                    if (status == 409) {
                        return new TxResponse(true, txToken, 0, null, "completed", null);
                    }
                    String msg = json.has("message") ? json.get("message").getAsString()
                               : httpStatusMessage(status);
                    return new TxResponse(false, txToken, 0, null, null, msg);
                }
            } catch (Exception e) {
                String error = "取引確定エラー: " + e.getMessage();
                logErrorOnce("approve_tx_error", error);
                return new TxResponse(false, txToken, 0, null, null, error);
            }
        });
    }

    /**
     * 取引をキャンセルする
     * POST /api/server/tx/cancel
     */
    public CompletableFuture<TxResponse> cancelTx(String txToken) {
        return cancelTx(txToken, null);
    }

    /** 取引をキャンセルする（代行操作対応） */
    public CompletableFuture<TxResponse> cancelTx(String txToken, String delegateMcId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(apiUrl + "/api/server/tx/cancel").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "KaisabaPoints/Minecraft");
                if (delegateMcId != null && !delegateMcId.isEmpty()) {
                    conn.setRequestProperty("x-delegate-mc-id", delegateMcId);
                }
                conn.setDoOutput(true);
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);

                JsonObject body = new JsonObject();
                body.addProperty("tx_token", txToken);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input);
                }

                int status = conn.getResponseCode();
                String responseBody = readResponse(conn);
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                if (status == 200 && json.has("success") && json.get("success").getAsBoolean()) {
                    return new TxResponse(true, txToken, 0, null, "cancelled", null);
                } else {
                    String msg = json.has("message") ? json.get("message").getAsString()
                               : httpStatusMessage(status);
                    return new TxResponse(false, txToken, 0, null, null, msg);
                }
            } catch (Exception e) {
                String error = "取引キャンセルエラー: " + e.getMessage();
                logErrorOnce("cancel_tx_error", error);
                return new TxResponse(false, txToken, 0, null, null, error);
            }
        });
    }

    // ========== アカウントリンク（維持） ==========

    /**
     * Minecraftアカウントをポイントアカウントにリンクする
     */
    public CompletableFuture<LinkResponse> linkAccount(String minecraftUsername, String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(apiUrl + "/link").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "KaisabaPoints/Minecraft");
                conn.setDoOutput(true);
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);

                JsonObject json = new JsonObject();
                json.addProperty("minecraft_username", minecraftUsername);
                json.addProperty("token", token);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input);
                }

                int status = conn.getResponseCode();
                String responseBody = readResponse(conn);
                JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();

                if (status == 200 && responseJson.has("success") && responseJson.get("success").getAsBoolean()) {
                    String msg = responseJson.has("message") ? responseJson.get("message").getAsString() : "リンクしました";
                    return new LinkResponse(true, msg);
                } else {
                    String msg = responseJson.has("message") ? responseJson.get("message").getAsString()
                               : httpStatusMessage(status);
                    return new LinkResponse(false, msg);
                }
            } catch (Exception e) {
                String error = "アカウントリンクエラー: " + e.getMessage();
                logErrorOnce("link_account_error", error);
                return new LinkResponse(false, error);
            }
        });
    }

    // ========== サーバーステータス取得 ==========

    /**
     * サーバーアカウントのステータスを取得する
     * GET /api/server/status
     * data.is_trusted で信頼モードかどうかがわかる
     */
    public CompletableFuture<ServerStatusResponse> getServerStatus() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(apiUrl + "/api/server/status").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("User-Agent", "KaisabaPoints/Minecraft");
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);

                int status = conn.getResponseCode();
                String body = readResponse(conn);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                if (status == 200 && json.has("success") && json.get("success").getAsBoolean()) {
                    JsonObject data = json.getAsJsonObject("data");
                    boolean isTrusted = data != null && data.has("is_trusted") && data.get("is_trusted").getAsBoolean();
                    return new ServerStatusResponse(true, isTrusted, null);
                } else {
                    String msg = json.has("message") ? json.get("message").getAsString()
                               : httpStatusMessage(status);
                    return new ServerStatusResponse(false, false, msg);
                }
            } catch (Exception e) {
                String error = "サーバーステータス取得エラー: " + e.getMessage();
                logErrorOnce("get_server_status_error", error);
                return new ServerStatusResponse(false, false, error);
            }
        });
    }

    // ========== レスポンスクラス ==========

    /** プレイヤー情報レスポンス */
    public static class PlayerResponse {
        public final boolean success;
        public final String mcId;
        public final int points;
        public final String message;

        public PlayerResponse(boolean success, String mcId, int points, String message) {
            this.success = success;
            this.mcId = mcId;
            this.points = points;
            this.message = message;
        }
    }

    /** 商品情報レスポンス */
    public static class ProductResponse {
        public final int id;
        public final String name;
        public final int price;

        public ProductResponse(int id, String name, int price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }
    }

    /** 取引レスポンス（initiate / getTxStatus / approveTx / cancelTx 共通） */
    public static class TxResponse {
        public final boolean success;
        public final String txToken;
        public final int amount;
        public final String webUrl;
        /** pending_buyer / pending_seller / completed / rejected / expired / cancelled */
        public final String status;
        public final String message;

        public TxResponse(boolean success, String txToken, int amount,
                          String webUrl, String status, String message) {
            this.success = success;
            this.txToken = txToken;
            this.amount = amount;
            this.webUrl = webUrl;
            this.status = status;
            this.message = message;
        }
    }

    /** アカウントリンクレスポンス */
    public static class LinkResponse {
        public final boolean success;
        public final String message;

        public LinkResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    /** サーバーステータスレスポンス */
    public static class ServerStatusResponse {
        public final boolean success;
        /** true = 信頼モード（管理者がAPIサーバー側で設定）*/
        public final boolean isTrusted;
        public final String message;

        public ServerStatusResponse(boolean success, boolean isTrusted, String message) {
            this.success = success;
            this.isTrusted = isTrusted;
            this.message = message;
        }
    }
}
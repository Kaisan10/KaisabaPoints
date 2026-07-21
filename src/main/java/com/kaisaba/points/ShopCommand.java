package com.kaisaba.points;

import com.kaisaba.points.gui.AdminShopMenu;
import com.kaisaba.points.gui.DeleteConfirmGui;
import com.kaisaba.points.gui.PlayerShopGui;
import com.kaisaba.points.gui.ShopListGui;
import com.kaisaba.points.listener.ShopGuiListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final KaisabaPoints plugin;
    private final ShopGuiListener listener;

    public ShopCommand(KaisabaPoints plugin, ShopGuiListener listener) {
        this.plugin = plugin;
        this.listener = listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }
        Player player = (Player) sender;

        // /shop → ショップ一覧
        if (args.length == 0) {
            new ShopListGui(plugin).open(player);
            return true;
        }

        String sub = args[0];

        // /shop create <id> <表示名...>
        if (sub.equalsIgnoreCase("create")) {
            boolean isAdmin = player.hasPermission("kaisabapoints.shop.admin");
            boolean hasCreatePerm = player.hasPermission("kaisabapoints.shop.create");
            boolean playerCreateEnabled = plugin.isShopPlayerCreateEnabled();

            // 許可条件: 管理者 OR shop.create権限あり OR プレイヤー作成が有効
            if (!isAdmin && !hasCreatePerm && !playerCreateEnabled) {
                player.sendMessage(ChatColor.RED + "現在プレイヤーはショップを作成できません。");
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "使い方: /shop create <id> <表示名>");
                return true;
            }
            String id = args[1];
            if (!id.matches("[a-zA-Z0-9_]+")) {
                player.sendMessage(ChatColor.RED + "IDは英数字とアンダースコアのみ使用できます。");
                return true;
            }
            if (plugin.getShopManager().getShop(id) != null) {
                player.sendMessage(ChatColor.RED + "そのIDのショップはすでに存在します。");
                return true;
            }
            // 表示名は残りの引数を結合
            StringBuilder displayName = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) displayName.append(" ");
                displayName.append(args[i]);
            }
            // 信頼モードをリアルタイムで確認（キャッシュ不使用）
            final String shopId = id;
            final String displayNameStr = displayName.toString();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean isTrusted = false;
                PointsAPI.ServerStatusResponse statusRes = plugin.getPointsAPI().getServerStatus().join();
                if (statusRes != null && statusRes.success) {
                    isTrusted = statusRes.isTrusted;
                }
                final boolean finalIsTrusted = isTrusted;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // 信頼モードのサーバーは owner = プレイヤー名（個人ショップ）、非信頼モードは owner = null（管理者ショップ）
                    String owner = finalIsTrusted ? player.getName() : null;
                    plugin.getShopManager().createShop(shopId, displayNameStr, owner);
                    ShopManager.ShopData shopData = plugin.getShopManager().getShop(shopId);
                    listener.registerAdminMenu(player, shopId);
                    new AdminShopMenu(plugin, shopData).open(player);
                });
            });
            return true;
        }

        // /shop edit <id>
        if (sub.equalsIgnoreCase("edit")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "使い方: /shop edit <id>");
                return true;
            }
            String id = args[1];
            ShopManager.ShopData shopData = plugin.getShopManager().getShop(id);
            if (shopData == null) {
                player.sendMessage(ChatColor.RED + "ショップ「" + id + "」が見つかりません。");
                return true;
            }
            // オーナー本人、または管理者構限持ちのみ編集可
            boolean isOwner = player.getName().equals(shopData.owner);
            boolean isAdmin = player.hasPermission("kaisabapoints.shop.admin");
            if (!isOwner && !isAdmin) {
                player.sendMessage(ChatColor.RED + "そのショップのオーナーまたは管理者でないと編集できません。");
                return true;
            }
            listener.registerAdminMenu(player, id);
            new AdminShopMenu(plugin, shopData).open(player);
            return true;
        }

        // /shop delete <id>
        if (sub.equalsIgnoreCase("delete")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "使い方: /shop delete <id>");
                return true;
            }
            String id = args[1];
            ShopManager.ShopData shopData = plugin.getShopManager().getShop(id);
            if (shopData == null) {
                player.sendMessage(ChatColor.RED + "ショップ「" + id + "」が見つかりません。");
                return true;
            }
            // オーナー本人、または管理者構限持ちのみ削除可
            boolean isOwner = player.getName().equals(shopData.owner);
            boolean isAdmin = player.hasPermission("kaisabapoints.shop.admin");
            if (!isOwner && !isAdmin) {
                player.sendMessage(ChatColor.RED + "そのショップのオーナーまたは管理者でないと削除できません。");
                return true;
            }
            listener.registerDeleteConfirm(player, id);
            new DeleteConfirmGui(plugin, shopData).open(player);
            return true;
        }

        // /shop <id> → 直接ショップを開く
        String id = sub;
        ShopManager.ShopData shopData = plugin.getShopManager().getShop(id);
        if (shopData == null) {
            player.sendMessage(ChatColor.RED + "ショップ「" + id + "」が見つかりません。");
            return true;
        }
        if (shopData.editMode) {
            player.sendMessage(ChatColor.RED + "[ショップ] このショップは現在編集中です。");
            return true;
        }
        new PlayerShopGui(plugin, shopData).open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();

        if (args.length == 1) {
            List<String> list = new ArrayList<>(Arrays.asList("create", "edit", "delete"));
            for (String id : plugin.getShopManager().getAllShops().keySet()) {
                list.add(id);
            }
            StringUtil.copyPartialMatches(args[0], list, result);
            Collections.sort(result);
            return result;
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("edit")
                    || args[0].equalsIgnoreCase("create")
                    || args[0].equalsIgnoreCase("delete")) {
                for (String id : plugin.getShopManager().getAllShops().keySet()) {
                    result.add(id);
                }
            }
        }

        // 入力中のテキストでフィルタ
        List<String> filtered = new ArrayList<>();
        StringUtil.copyPartialMatches(args[args.length - 1], result, filtered);
        Collections.sort(filtered);
        return filtered;
    }
}

package com.kaisaba.points;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PointsCommand implements CommandExecutor {
    
    private final KaisabaPoints plugin;
    private final PointsAPI api;
    
    public PointsCommand(KaisabaPoints plugin, PointsAPI api) {
        this.plugin = plugin;
        this.api = api;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます");
            return true;
        }
        
        Player player = (Player) sender;
        
        // /points reload
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("kaisabapoints.reload")) {
                sender.sendMessage(ChatColor.RED + "権限がありません");
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(ChatColor.GREEN + "設定を再読み込みしました。");
            return true;
        }
        
        // /points link <token>
        if (args.length >= 1 && args[0].equalsIgnoreCase("link")) {
            if (args.length != 2) {
                player.sendMessage(ChatColor.RED + "使い方: /pt link <コード>");
                player.sendMessage(ChatColor.GRAY + "コードは https://points.bac0n.f5.si/link で生成できます");
                return true;
            }
            
            String token = args[1].toUpperCase();

            api.linkAccount(player.getName(), token).thenAccept(response -> {
                if (response.success) {
                    player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "------------------------");
                    player.sendMessage(ChatColor.GOLD + "リンク成功！");
                    player.sendMessage(ChatColor.WHITE + response.message);
                    player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "------------------------");
                } else {
                    player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "------------------------");
                    player.sendMessage(ChatColor.RED + "リンク失敗");
                    player.sendMessage(ChatColor.WHITE + response.message);
                    player.sendMessage(ChatColor.GRAY + "コードを確認するか、新しいコードを生成してください");
                    player.sendMessage(ChatColor.YELLOW + "https://points.bac0n.f5.si/link");
                    player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "------------------------");
                }
            }).exceptionally(e -> {
                player.sendMessage(ChatColor.RED + "エラーが発生しました、このエラーの内容をフォーラムで報告してください。");
                player.sendMessage(ChatColor.GRAY + "https://forum.bac0n.f5.si/");
                e.printStackTrace();
                return null;
            });
            
            return true;
        }
        
        // 自分のポイントを確認
        if (args.length == 0) {

            api.getPlayerInfo(player.getName()).thenAccept(response -> {
                if (response.success) {
                    player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "------------------------");
                    player.sendMessage(ChatColor.GOLD + "あなたのポイント");
                    player.sendMessage(ChatColor.YELLOW + "ポイント: " + ChatColor.AQUA + response.points);
                    player.sendMessage(ChatColor.YELLOW + "ユーザー名: " + ChatColor.WHITE + response.mcId);
                    player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "------------------------");
                } else {
                    player.sendMessage(ChatColor.RED + response.message);
                    player.sendMessage(ChatColor.GRAY + "フォーラムでアカウントを作成してください");
                }
            }).exceptionally(e -> {
                player.sendMessage(ChatColor.RED + "エラーが発生しました、このエラーの内容をフォーラムで報告してください。");
                e.printStackTrace();
                return null;
            });
            
            return true;
        }
        
        // 他のプレイヤーのポイントを確認
        if (args.length == 1) {
            if (!player.hasPermission("kaisabapoints.check.others")) {
                player.sendMessage(ChatColor.RED + "権限がありません");
                return true;
            }
            
            String targetName = args[0];

            api.getPlayerInfo(targetName).thenAccept(response -> {
                if (response.success) {
                    player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "------------------------");
                    player.sendMessage(ChatColor.GOLD + response.mcId + "のポイント");
                    player.sendMessage(ChatColor.YELLOW + "ポイント: " + ChatColor.AQUA + response.points);
                    player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "------------------------");
                } else {
                    player.sendMessage(ChatColor.RED + response.message);
                }
            }).exceptionally(e -> {
                player.sendMessage(ChatColor.RED + "エラーが発生しました、このエラーの内容をフォーラムで報告してください。");
                e.printStackTrace();
                return null;
            });
            
            return true;
        }
        
        player.sendMessage(ChatColor.RED + "使い方: /points [プレイヤー名]");
        return true;
    }
}
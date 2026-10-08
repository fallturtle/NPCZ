package hiddenknight5.skriptautoreloader;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class SkriptAutoReloader extends JavaPlugin {

    private BukkitTask reloadTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        final long startupDelay = Math.max(1L, getConfig().getLong("startup-delay-seconds", 10L) * 20L);
        final long interval = Math.max(1L, getConfig().getLong("reload-interval-minutes", 30L) * 60L * 20L);

        reloadTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            private boolean firstRun = true;

            @Override
            public void run() {
                reloadSkript();
                if (firstRun) {
                    firstRun = false;
                }
            }
        }, startupDelay, interval);

        getLogger().info("SkriptAutoReloader enabled. First reload in "
                + getConfig().getLong("startup-delay-seconds", 10L)
                + " seconds, then every "
                + getConfig().getLong("reload-interval-minutes", 30L)
                + " minutes.");
    }

    private void reloadSkript() {
        String skript = getConfig().getString("skript-name", "superweapons");

        if (Bukkit.getPluginManager().getPlugin("Skript") == null) {
            getLogger().warning("Skript is not installed; cannot reload " + skript + ".");
            return;
        }

        ConsoleCommandSender console = Bukkit.getConsoleSender();
        String command = "sk reload " + skript;

        getLogger().info("Running: /" + command);
        Bukkit.dispatchCommand(console, command);
    }

    @Override
    public void onDisable() {
        if (reloadTask != null) {
            reloadTask.cancel();
        }
    }
}
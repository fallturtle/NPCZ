package hiddenknight5.worldevents;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public class WorldEventsPlugin extends JavaPlugin implements Listener, CommandExecutor {
    public final Random random = new Random();
    public String active = null;
    public long endTick = 0;
    public long nextRandomDay = -1;
    public EventManager events;
    public TreasureManager treasures;

    @Override public void onEnable() {
        saveDefaultConfig();
        events = new EventManager(this);
        treasures = new TreasureManager(this);
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("event").setExecutor(this);
        schedule();
        if (getConfig().getBoolean("random-events.enabled", true) && nextRandomDay < 0) scheduleNext();
        getLogger().info("WorldEvents 1.0.0 enabled.");
    }

    @Override public void onDisable() {
        getConfig().set("next-random-day", nextRandomDay);
        saveConfig();
        events.cancelTasks();
    }

    public World world() { return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0); }
    public long day() { return world() == null ? 0 : world().getFullTime()/24000L; }

    public void scheduleNext() {
        int min=Math.max(1,getConfig().getInt("random-events.minimum-days",1));
        int max=Math.max(min,getConfig().getInt("random-events.maximum-days",10));
        nextRandomDay=day()+min+random.nextInt(max-min+1);
        getConfig().set("next-random-day",nextRandomDay);
        saveConfig();
    }

    private void schedule() {
        new org.bukkit.scheduler.BukkitRunnable(){ public void run() {
            World w=world(); if(w==null)return;
            if(active!=null && endTick>0 && w.getFullTime()>=endTick) events.stop(true);
            if(active==null && getConfig().getBoolean("random-events.enabled",true) && nextRandomDay>=0 && day()>=nextRandomDay){
                events.randomEvent();
                scheduleNext();
            }
        }}.runTaskTimer(this,20L,20L);
    }

    @EventHandler public void shrine(BlockPlaceEvent e) { events.checkHerobrine(e); }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if(!s.hasPermission("worldevents.admin")) { s.sendMessage("§cNo permission."); return true; }
        if(a.length==0){help(s);return true;}
        if(a[0].equalsIgnoreCase("list")){s.sendMessage("§dEvents: §f"+EventManager.NAMES);return true;}
        if(a[0].equalsIgnoreCase("status")){s.sendMessage("§dActive: §f"+(active==null?"none":active));return true;}
        if(a[0].equalsIgnoreCase("stop")){events.stop(true);return true;}
        if(a[0].equalsIgnoreCase("start")&&a.length>1){events.start(a[1]);return true;}
        if(a[0].equalsIgnoreCase("random")&&a.length>1){getConfig().set("random-events.enabled",a[1].equalsIgnoreCase("on"));saveConfig();s.sendMessage("§aRandom events updated.");return true;}
        if(a[0].equalsIgnoreCase("reload")){reloadConfig();s.sendMessage("§aWorldEvents reloaded.");return true;}
        if(a[0].equalsIgnoreCase("treasure")){treasures.command(s,a);return true;}
        help(s); return true;
    }

    private void help(CommandSender s){
        s.sendMessage("§d§lWorldEvents");
        s.sendMessage("§e/event list §7| §e/event start <event> §7| §e/event stop");
        s.sendMessage("§e/event status §7| §e/event random <on/off> §7| §e/event reload");
        s.sendMessage("§e/event treasure create/clue/setfinal/start/delete");
    }
}
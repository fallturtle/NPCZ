package hiddenknight5.worldevents;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public class TreasureManager {
    final WorldEventsPlugin p;
    final Map<String,List<String>> clues=new HashMap<>();
    final Map<String,Location> finals=new HashMap<>();
    TreasureManager(WorldEventsPlugin p){this.p=p;}

    void normal(){
        Player x=player();if(x==null)return;
        Location l=p.events.safeNear(x.getLocation(),p.getConfig().getInt("events.treasure-hunt.radius",1000));
        Block b=l.getBlock();b.setType(Material.CHEST);Chest c=(Chest)b.getState();
        c.getInventory().addItem(new ItemStack(Material.DIAMOND,3+p.random.nextInt(5)),new ItemStack(Material.GOLDEN_APPLE,1+p.random.nextInt(2)),new ItemStack(Material.SKULL_ITEM,1,(short)1));
        if(p.random.nextInt(100)<35)c.getInventory().addItem(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
        if(p.random.nextInt(100)<12)c.getInventory().addItem(new ItemStack(Material.ELYTRA));c.update();
        Bukkit.broadcastMessage("§8[§5WorldEvents§8] §7"+mysterious(l));
    }

    String mysterious(Location l){
        String b=l.getBlock().getBiome().name();
        if(b.contains("FOREST"))return ""Where the green walls close around the forgotten path, something waits beneath the leaves."";
        if(b.contains("DESERT"))return ""Where the world becomes dust and the wind erases footsteps, a secret refuses to be buried."";
        if(b.contains("TAIGA"))return ""Among the cold trees, where the ground remembers winter, the forgotten thing sleeps."";
        if(b.contains("OCEAN"))return ""Beyond the reach of dry feet, the silence keeps what the waves refuse to reveal."";
        if(b.contains("MOUNTAIN")||b.contains("HILLS"))return ""Above the lowlands, where stone breaks the sky, an old secret watches the valleys below."";
        return ""The path is not marked. Look for the place the world seems to have forgotten."";
    }

    void command(CommandSender s,String[] a){
        if(a.length<2){s.sendMessage("§e/event treasure create <name> | clue <name> <text> | setfinal <name> | start <name> | delete <name>");return;}
        String sub=a[1].toLowerCase();
        if(sub.equals("create")&&a.length>2){clues.put(a[2].toLowerCase(),new ArrayList<String>());s.sendMessage("§aCreated treasure hunt §e"+a[2]);return;}
        if(sub.equals("clue")&&a.length>3){List<String> list=clues.get(a[2].toLowerCase());if(list==null){s.sendMessage("§cHunt doesn't exist.");return;}list.add(String.join(" ",Arrays.copyOfRange(a,3,a.length)));s.sendMessage("§aMysterious clue added.");return;}
        if(sub.equals("setfinal")&&a.length>2&&s instanceof Player){finals.put(a[2].toLowerCase(),((Player)s).getLocation().getBlock().getLocation());s.sendMessage("§aFinal location saved.");return;}
        if(sub.equals("start")&&a.length>2){start(a[2].toLowerCase());return;}
        if(sub.equals("delete")&&a.length>2){clues.remove(a[2].toLowerCase());finals.remove(a[2].toLowerCase());s.sendMessage("§aDeleted.");}
    }

    void start(String name){
        if(p.active!=null){Bukkit.broadcastMessage("§cAn event is already active.");return;}
        Location l=finals.get(name);List<String> cs=clues.get(name);
        if(l==null||cs==null){Bukkit.broadcastMessage("§cTreasure hunt setup incomplete: "+name);return;}
        p.active="treasure:"+name;Bukkit.broadcastMessage("§d§l✦ A HIDDEN TREASURE HUNT HAS BEGUN ✦");
        for(int i=0;i<cs.size();i++){final String clue=cs.get(i);final int n=i+1;Bukkit.getScheduler().runTaskLater(p,()->Bukkit.broadcastMessage("§7Clue "+n+": §f"+ChatColor.translateAlternateColorCodes('&',clue)),40L+i*200L);}
        long delay=40L+cs.size()*200L+40L;Bukkit.getScheduler().runTaskLater(p,()->place(l),delay);
        p.endTick=p.world().getFullTime()+Math.max(2400L,delay+600L);
    }

    void place(Location l){
        Block b=l.getBlock();if(b.getType()!=Material.AIR)b=l.clone().add(0,1,0).getBlock();b.setType(Material.CHEST);Chest c=(Chest)b.getState();
        c.getInventory().addItem(new ItemStack(Material.DIAMOND,5),new ItemStack(Material.GOLDEN_APPLE,2),new ItemStack(Material.ELYTRA));c.update();
        Bukkit.broadcastMessage("§8[§5WorldEvents§8] §6The final secret has been found.");
    }

    Player player(){List<Player> l=new ArrayList<>(Bukkit.getOnlinePlayers());return l.isEmpty()?null:l.get(p.random.nextInt(l.size()));}
}
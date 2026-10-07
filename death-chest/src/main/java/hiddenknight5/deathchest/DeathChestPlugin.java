package hiddenknight5.deathchest;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class DeathChestPlugin extends JavaPlugin implements Listener {
 private final Map<String,DeathChest> chests=new HashMap<String,DeathChest>();
 private File dataFile; private YamlConfiguration data; private int revealMinutes,xpPerBottle,taskId;

 public void onEnable(){
  saveDefaultConfig();
  revealMinutes=Math.max(1,getConfig().getInt("reveal-time-minutes",10));
  xpPerBottle=Math.max(1,getConfig().getInt("xp-points-per-bottle",3));
  dataFile=new File(getDataFolder(),"deathchests.yml"); data=YamlConfiguration.loadConfiguration(dataFile); loadChests();
  Bukkit.getPluginManager().registerEvents(this,this);
  taskId=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,new Runnable(){public void run(){tick();}},20L,20L);
 }
 public void onDisable(){if(taskId!=0)Bukkit.getScheduler().cancelTask(taskId);saveChests();}

 @EventHandler(priority=EventPriority.HIGHEST)
 public void death(PlayerDeathEvent e){
  Player p=e.getEntity(); List<ItemStack> items=new ArrayList<ItemStack>();
  for(ItemStack i:p.getInventory().getContents())if(valid(i))items.add(i.clone());
  for(ItemStack i:p.getInventory().getArmorContents())if(valid(i))items.add(i.clone());
  int xp=Math.max(0,p.getTotalExperience()), bottles=xp==0?0:(xp+xpPerBottle-1)/xpPerBottle;
  for(int i=0;i<bottles;i++)items.add(new ItemStack(Material.EXP_BOTTLE));
  if(items.isEmpty())return;
  Location l=findLocation(p.getLocation());
  if(l==null){e.getDrops().clear();e.getDrops().addAll(items);e.setDroppedExp(0);p.sendMessage(color(msg("no-space")));return;}
  e.getDrops().clear();e.setDroppedExp(0);p.getInventory().clear();p.setTotalExperience(0);p.setLevel(0);p.setExp(0);
  l.getBlock().setType(Material.CHEST);l.clone().add(1,0,0).getBlock().setType(Material.CHEST);
  Block a=l.getBlock(),b=l.clone().add(1,0,0).getBlock();Inventory inv=((Chest)a.getState()).getInventory();
  for(ItemStack i:items)inv.addItem(i);
  DeathChest dc=new DeathChest(p.getUniqueId(),p.getName(),a,b,System.currentTimeMillis()+revealMinutes*60000L);
  chests.put(key(a),dc);chests.put(key(b),dc);saveChests();
  p.sendMessage(color(msg("private-location").replace("{x}",""+a.getX()).replace("{y}",""+a.getY()).replace("{z}",""+a.getZ())));
 }

 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
 public void breakChest(BlockBreakEvent e){if(chests.containsKey(key(e.getBlock())))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
 public void entityExplode(EntityExplodeEvent e){protect(e.blockList());}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
 public void blockExplode(BlockExplodeEvent e){protect(e.blockList());}
 private void protect(List<Block> bs){Iterator<Block> it=bs.iterator();while(it.hasNext())if(chests.containsKey(key(it.next())))it.remove();}

 @EventHandler public void close(InventoryCloseEvent e){for(DeathChest dc:unique())if(dc.empty()){remove(dc,true);return;}}

 private void tick(){
  long now=System.currentTimeMillis();
  for(DeathChest dc:unique()){
   if(!exists(dc)){chests.remove(key(dc.a));chests.remove(key(dc.b));continue;}
   if(dc.empty()){remove(dc,true);continue;}
   long left=dc.deadline-now;
   if(left<=0){
    if(!dc.revealed){dc.revealed=true;Location l=dc.a.getLocation();Bukkit.broadcastMessage(color(msg("revealed").replace("{player}",dc.name).replace("{x}",""+l.getBlockX()).replace("{y}",""+l.getBlockY()).replace("{z}",""+l.getBlockZ())));saveChests();}
    continue;
   }
   long mins=(left+59999L)/60000L;
   if(mins!=dc.lastMinute){
    dc.lastMinute=mins;Player p=Bukkit.getPlayer(dc.owner);
    if(p!=null&&p.isOnline())p.sendMessage(color(msg("countdown").replace("{time}",format(left))));
   }
  }
 }
 private void remove(DeathChest dc,boolean claimed){
  chests.remove(key(dc.a));chests.remove(key(dc.b));
  if(dc.a.getType()==Material.CHEST)dc.a.setType(Material.AIR);if(dc.b.getType()==Material.CHEST)dc.b.setType(Material.AIR);
  if(claimed&&!dc.revealed){Player p=Bukkit.getPlayer(dc.owner);if(p!=null)p.sendMessage(color(msg("claimed")));}
  saveChests();
 }
 private Location findLocation(Location d){
  World w=d.getWorld();int bx=d.getBlockX(),by=d.getBlockY(),bz=d.getBlockZ();
  for(int r=0;r<=3;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++)for(int dy=-1;dy<=2;dy++){
   int x=bx+dx,y=by+dy,z=bz+dz;if(y<=0||y>=w.getMaxHeight()-1)continue;
   Location a=new Location(w,x,y,z),b=new Location(w,x+1,y,z);
   if(can(a)&&can(b))return a;
  }return null;
 }
 private boolean can(Location l){Block b=l.getBlock();return b.getType()==Material.AIR&&b.getRelative(0,-1,0).getType().isSolid();}
 private boolean valid(ItemStack i){return i!=null&&i.getType()!=Material.AIR;}
 private boolean exists(DeathChest d){return d.a.getType()==Material.CHEST&&d.b.getType()==Material.CHEST;}
 private String key(Block b){return b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
 private String msg(String s){return getConfig().getString("messages."+s,"");}
 private String color(String s){return ChatColor.translateAlternateColorCodes('&',s);}
 private String format(long ms){long s=Math.max(1,(ms+999)/1000),m=s/60,x=s%60;return m>0?m+"m"+(x>0?" "+x+"s":""):x+"s";}
 private Set<DeathChest> unique(){return new HashSet<DeathChest>(chests.values());}

 private void loadChests(){
  ConfigurationSection root=data.getConfigurationSection("chests");if(root==null)return;
  for(String id:root.getKeys(false))try{
   String p="chests."+id;UUID u=UUID.fromString(data.getString(p+".owner"));World w=Bukkit.getWorld(UUID.fromString(data.getString(p+".world")));if(w==null)continue;
   Block a=w.getBlockAt(data.getInt(p+".x"),data.getInt(p+".y"),data.getInt(p+".z")),b=w.getBlockAt(data.getInt(p+".x2"),data.getInt(p+".y2"),data.getInt(p+".z2"));
   if(a.getType()!=Material.CHEST||b.getType()!=Material.CHEST)continue;
   DeathChest dc=new DeathChest(u,data.getString(p+".player"),a,b,data.getLong(p+".deadline"));dc.revealed=data.getBoolean(p+".revealed");chests.put(key(a),dc);chests.put(key(b),dc);
  }catch(Exception ignored){}
 }
 private void saveChests(){
  data=new YamlConfiguration();int i=0;for(DeathChest d:unique()){String p="chests."+(i++);
   data.set(p+".owner",d.owner.toString());data.set(p+".player",d.name);data.set(p+".world",d.a.getWorld().getUID().toString());
   data.set(p+".x",d.a.getX());data.set(p+".y",d.a.getY());data.set(p+".z",d.a.getZ());data.set(p+".x2",d.b.getX());data.set(p+".y2",d.b.getY());data.set(p+".z2",d.b.getZ());data.set(p+".deadline",d.deadline);data.set(p+".revealed",d.revealed);
  }try{if(!getDataFolder().exists())getDataFolder().mkdirs();data.save(dataFile);}catch(IOException ex){getLogger().warning("Could not save death chests: "+ex.getMessage());}
 }
 private static class DeathChest{
  final UUID owner;final String name;final Block a,b;final long deadline;boolean revealed;long lastMinute=-1;
  DeathChest(UUID o,String n,Block a,Block b,long d){owner=o;name=n;this.a=a;this.b=b;deadline=d;}
  boolean empty(){if(a.getType()!=Material.CHEST||b.getType()!=Material.CHEST)return true;for(ItemStack i:((Chest)a.getState()).getInventory().getContents())if(i!=null&&i.getType()!=Material.AIR)return false;return true;}
 }
}
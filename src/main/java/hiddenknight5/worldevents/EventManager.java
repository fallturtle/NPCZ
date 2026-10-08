package hiddenknight5.worldevents;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public class EventManager {
    static final String NAMES="bloodmoon, purge, meteor_shower, supply_drop, zombie_outbreak, boss_attack, eclipse, abandoned_structure, treasure_hunt, inferno, unknown_signal";
    final WorldEventsPlugin p;
    BukkitRunnable task;
    EventManager(WorldEventsPlugin p){this.p=p;}

    void start(String type){
        if(p.active!=null){msg("&cAn event is already active: &e"+p.active);return;}
        String t=type.toLowerCase().replace('-','_');
        if(!Arrays.asList(NAMES.split(", ")).contains(t)){msg("&cUnknown event.");return;}
        p.active=t; World w=p.world(); if(w==null){p.active=null;return;}
        switch(t){
            case "bloodmoon": blood(w);break; case "purge": timed(w,"purge",10);break;
            case "meteor_shower": meteor(w);break; case "supply_drop": supply(w);break;
            case "zombie_outbreak": outbreak(w);break; case "boss_attack": boss(w);break;
            case "eclipse": eclipse(w);break; case "abandoned_structure": ruin(w);break;
            case "treasure_hunt": treasure(w);break; case "inferno": inferno(w);break;
            case "unknown_signal": signal(w);break;
        }
    }

    void randomEvent(){
        String[] n=NAMES.split(", "); List<String> pool=new ArrayList<>();
        for(String s:n){int w=p.getConfig().getInt("random-event-weights."+s,1);for(int i=0;i<w;i++)pool.add(s);}
        if(!pool.isEmpty())start(pool.get(p.random.nextInt(pool.size())));
    }

    void blood(World w){
        w.setTime(13000); p.endTick=w.getFullTime()+10000;
        msg("&4&l☾ BLOOD MOON ☽");msg("&cThe night feels wrong. Survive until dawn.");
        repeat(100,200,()->{for(Player x:Bukkit.getOnlinePlayers())spawnMob(x,EntityType.ZOMBIE,"&4Blood Moon Zombie",8);});
    }

    void timed(World w,String name,int def){
        long mins=p.getConfig().getInt("events."+name+".duration-minutes",def);
        p.endTick=w.getFullTime()+mins*1200L;
        msg("&4&l☠ THE PURGE HAS BEGUN ☠");msg("&cFor a limited time, the world has no mercy.");
    }

    void meteor(World w){
        int count=p.getConfig().getInt("events.meteor-shower.meteors",12);
        int radius=p.getConfig().getInt("events.meteor-shower.radius",120);
        msg("&5&l☄ METEOR SHOWER ☄");msg("&dSomething is falling from the sky...");
        task=new BukkitRunnable(){int left=count;public void run(){
            if(p.active==null||!p.active.equals("meteor_shower")||left--<=0){stop(false);cancel();return;}
            Player x=player();if(x==null)return;Location l=safeNear(x.getLocation(),radius);
            if(l==null)return;l.getWorld().strikeLightningEffect(l);l.getWorld().createExplosion(l,(float)p.getConfig().getDouble("events.meteor-shower.explosion-power",2),false,false);
            if(p.random.nextInt(100)<25)l.getWorld().dropItemNaturally(l,new ItemStack(Material.DIAMOND,1+p.random.nextInt(3)));
        }};task.runTaskTimer(p,40,60);
    }

    void supply(World w){
        Player x=player();if(x==null){stop(false);return;}Location l=safeNear(x.getLocation(),p.getConfig().getInt("events.supply-drop.radius",300));
        if(l==null){stop(false);return;}l.getBlock().setType(Material.CHEST);Chest c=(Chest)l.getBlock().getState();
        c.getInventory().addItem(new ItemStack(Material.DIAMOND,2+p.random.nextInt(5)),new ItemStack(Material.GOLDEN_APPLE,1+p.random.nextInt(2)),new ItemStack(Material.ENCHANTED_BOOK));
        if(p.random.nextInt(100)<30)c.getInventory().addItem(new ItemStack(Material.SKULL_ITEM,1,(short)1));
        if(p.random.nextInt(100)<10)c.getInventory().addItem(new ItemStack(Material.ELYTRA));c.update();
        msg("&6&l📦 SUPPLY DROP");msg("&eA supply crate has fallen somewhere in the wilderness.");
        p.endTick=w.getFullTime()+1200;
    }

    void outbreak(World w){
        p.endTick=w.getFullTime()+p.getConfig().getInt("events.zombie-outbreak.duration-minecraft-days",1)*24000L;
        msg("&2&l☣ ZOMBIE OUTBREAK ☣");msg("&aThe dead are rising. Stay alive.");
        repeat(100,200,()->{for(Player x:Bukkit.getOnlinePlayers())spawnMob(x,EntityType.ZOMBIE,"&2Outbreak Zombie",4);});
    }

    void boss(World w){
        Player x=player();if(x==null){p.active=null;return;}Location l=safeNear(x.getLocation(),25);
        Zombie z=(Zombie)w.spawnEntity(l,EntityType.ZOMBIE);z.setCustomName("§4§lTHE WARDEN OF ASH");z.setCustomNameVisible(true);
        double hp=p.getConfig().getDouble("events.boss-attack.health",150);
        try{z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(hp);}catch(Exception ignored){}
        z.setHealth(Math.min(hp,z.getMaxHealth()));z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,999999,1));
        z.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));z.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));z.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        msg("&4&l⚔ BOSS ATTACK ⚔");msg("&cSomething powerful has entered the world.");p.endTick=w.getFullTime()+24000;
    }

    void eclipse(World w){
        p.endTick=w.getFullTime()+p.getConfig().getInt("events.eclipse.duration-minecraft-days",1)*24000L;w.setTime(18000);
        msg("&8&l☾ ECLIPSE ☽");msg("&7The daylight has vanished...");
    }

    void ruin(World w){
        Player x=player();if(x==null){p.active=null;return;}Location l=safeNear(x.getLocation(),p.getConfig().getInt("events.abandoned-structure.radius",500));if(l==null){p.active=null;return;}
        int X=l.getBlockX(),Y=l.getBlockY(),Z=l.getBlockZ();
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)w.getBlockAt(X+dx,Y,Z+dz).setType((Math.abs(dx)==3||Math.abs(dz)==3)?Material.MOSSY_COBBLESTONE:Material.AIR);
        for(int dx=-3;dx<=3;dx+=3)for(int dz=-3;dz<=3;dz+=3)for(int h=1;h<=3;h++)w.getBlockAt(X+dx,Y+h,Z+dz).setType(Material.MOSSY_COBBLESTONE);
        Block b=w.getBlockAt(X,Y+1,Z);b.setType(Material.CHEST);Chest c=(Chest)b.getState();c.getInventory().addItem(new ItemStack(Material.GOLD_INGOT,2+p.random.nextInt(5)),new ItemStack(Material.BONE,2+p.random.nextInt(8)));c.update();
        msg("&7Something old has been uncovered in the wilderness.");msg("&8No one knows who left it behind.");p.endTick=w.getFullTime()+1200;
    }

    void treasure(World w){p.treasures.normal();p.endTick=w.getFullTime()+2400;msg("&d&l✦ A TREASURE HUNT HAS BEGUN ✦");}
    
    void inferno(World w){
        p.endTick=w.getFullTime()+p.getConfig().getInt("events.inferno.duration-minutes",8)*1200L;msg("&c&l🔥 INFERNO 🔥");msg("&6The land is burning. Keep your distance.");
        int r=p.getConfig().getInt("events.inferno.fire-radius",35);
        for(Player x:Bukkit.getOnlinePlayers())for(int i=0;i<20;i++){Location l=safeNear(x.getLocation(),r);if(l!=null&&l.getBlock().getType()==Material.AIR&&l.clone().add(0,-1,0).getBlock().getType().isSolid())l.getBlock().setType(Material.FIRE);}
    }

    void signal(World w){
        p.endTick=w.getFullTime()+600;msg("&8&l[ UNKNOWN SIGNAL ]");msg("&7...");Bukkit.getScheduler().runTaskLater(p,()->msg("&7A signal has been detected. No source identified."),80);Bukkit.getScheduler().runTaskLater(p,()->msg("&8\"...do you hear it?\""),160);
    }

    void checkHerobrine(BlockPlaceEvent e){
        if(e.getBlockPlaced().getType()!=Material.FIRE||p.active!=null)return;Location l=e.getBlockPlaced().getLocation();
        if(l.clone().add(0,-1,0).getBlock().getType()!=Material.NETHERRACK)return;int gold=0;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(Math.abs(dx)+Math.abs(dz)==1&&l.getWorld().getBlockAt(l.getBlockX()+dx,l.getBlockY()-1,l.getBlockZ()+dz).getType()==Material.GOLD_BLOCK)gold++;
        if(gold>=3){msg("&8Something has noticed what you have built.");Bukkit.getScheduler().runTaskLater(p,()->herobrine(e.getPlayer()),60);}
    }

    void herobrine(Player x){
        if(p.active!=null)return;p.active="herobrine";World w=x.getWorld();Location l=safeNear(x.getLocation(),12);Zombie z=(Zombie)w.spawnEntity(l,EntityType.ZOMBIE);
        z.setCustomName("§4§lHEROBRINE");z.setCustomNameVisible(true);try{z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(200);}catch(Exception ignored){}
        z.setHealth(Math.min(200,z.getMaxHealth()));z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,999999,2));z.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE,999999,1));
        z.getEquipment().setHelmet(new ItemStack(Material.GOLD_BLOCK));msg("&4&lHEROBRINE HAS AWAKENED");msg("&8You should not have done that.");p.endTick=w.getFullTime()+24000;
    }

    void spawnMob(Player x,EntityType type,String name,double bonus){
        Location l=safeNear(x.getLocation(),25);if(l==null)return;Zombie z=(Zombie)x.getWorld().spawnEntity(l,type);z.setCustomName(name.replace('&','§'));
        try{z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(Math.min(40,z.getMaxHealth()+bonus));}catch(Exception ignored){}z.setHealth(z.getMaxHealth());
    }

    Player player(){List<Player> l=new ArrayList<>(Bukkit.getOnlinePlayers());return l.isEmpty()?null:l.get(p.random.nextInt(l.size()));}
    Location safeNear(Location c,int r){for(int i=0;i<40;i++){int x=c.getBlockX()+p.random.nextInt(r*2+1)-r,z=c.getBlockZ()+p.random.nextInt(r*2+1)-r,y=c.getWorld().getHighestBlockYAt(x,z)+1;Location l=new Location(c.getWorld(),x,y,z);Material g=l.clone().add(0,-1,0).getBlock().getType();if(g.isSolid()&&g!=Material.LAVA&&g!=Material.FIRE&&l.getBlock().getType()==Material.AIR)return l;}return c.clone().add(0,1,0);}
    void repeat(long delay,long period,final Runnable r){task=new BukkitRunnable(){public void run(){if(p.active==null){cancel();return;}r.run();}};task.runTaskTimer(p,delay,period);}
    void cancelTasks(){if(task!=null)task.cancel();}
    void stop(boolean announce){String old=p.active;if(old==null)return;p.active=null;p.endTick=0;cancelTasks();if(announce)msg("&aEvent ended: &e"+old);}
    void msg(String s){Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',p.getConfig().getString("messages.prefix","")+s));}
}
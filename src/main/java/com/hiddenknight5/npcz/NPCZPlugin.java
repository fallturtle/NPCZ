package com.hiddenknight5.npcz;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.io.*;import java.lang.reflect.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import java.util.concurrent.ConcurrentHashMap;

public class NPCZPlugin extends JavaPlugin implements Listener, CommandExecutor {
  private final Map<Integer,Map<String,String>> npcs=new LinkedHashMap<>();
  private File dataFile; private int nextId=1; private String rtpCommand="rtp";
  private Object server;
  private final Map<String,String> chatPrompts=new ConcurrentHashMap<>();
  private final Map<String,PendingSign> pendingSigns=new ConcurrentHashMap<>();
  public void onEnable(){ try{ server=call(this,"getServer"); dataFile=new File(getDataFolder(),"npcs.yml"); if(!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs(); load(); registerEvents(); registerCommand(); for(Map<String,String> n:npcs.values()) spawnStored(n); }catch(Exception e){e.printStackTrace();} }
  public void onDisable(){save();}
  private void registerEvents() { getServer().getPluginManager().registerEvents(this,this); }

  @EventHandler(priority=EventPriority.HIGHEST)
  public void onNpcInteract(PlayerInteractEntityEvent event){ try { Map<String,String> n=find(event.getRightClicked()); if(n!=null){ event.setCancelled(true); action(event.getPlayer(),n); } } catch(Exception e){e.printStackTrace();} }

  @EventHandler(priority=EventPriority.HIGHEST)
  public void onNpcDamage(EntityDamageEvent event){ try { if(find(event.getEntity())!=null) event.setCancelled(true); } catch(Exception e){e.printStackTrace();} }
  @EventHandler(priority=EventPriority.HIGHEST)
  public void onNpcExplode(EntityExplodeEvent event){ try { if(find(event.getEntity())!=null) event.setCancelled(true); } catch(Exception e){e.printStackTrace();} }
  @EventHandler(priority=EventPriority.HIGHEST)
  public void onNpcPrime(ExplosionPrimeEvent event){ try { if(find(event.getEntity())!=null) event.setCancelled(true); } catch(Exception e){e.printStackTrace();} }
  @EventHandler(priority=EventPriority.HIGHEST)
  public void onNpcCombust(EntityCombustEvent event){ try { if(find(event.getEntity())!=null) event.setCancelled(true); } catch(Exception e){e.printStackTrace();} }
  @EventHandler(priority=EventPriority.HIGHEST)
  public void onNpcTarget(EntityTargetEvent event){ try { if(find(event.getEntity())!=null) event.setCancelled(true); } catch(Exception e){e.printStackTrace();} }

  @EventHandler(priority=EventPriority.HIGHEST)
  public void onGuiClick(InventoryClickEvent event){ try { String title=event.getView().getTitle(); if(title!=null && title.startsWith("NPCZ #")){ event.setCancelled(true); int id=Integer.parseInt(title.substring(6)); guiClick(event.getWhoClicked(),id,event.getRawSlot()); } } catch(Exception e){e.printStackTrace();} }
  @EventHandler(priority=EventPriority.HIGHEST)
  public void onGuiDrag(InventoryDragEvent event){ try { String title=event.getView().getTitle(); if(title!=null && title.startsWith("NPCZ #")) event.setCancelled(true); } catch(Exception e){e.printStackTrace();} }

  @EventHandler(priority=EventPriority.HIGHEST)
  public void onSignChange(SignChangeEvent event){
    try{
      String playerName=event.getPlayer().getName();
      PendingSign pending=pendingSigns.remove(playerName);
      if(pending==null)return;
      StringBuilder value=new StringBuilder();
      for(String line:event.getLines()){
        if(line!=null && !line.isEmpty()){
          if(value.length()>0)value.append(" ");
          value.append(line);
        }
      }
      finishSignInput(event.getPlayer(),pending,value.toString());
      restoreSign(pending);
      event.setCancelled(true);
    }catch(Exception e){e.printStackTrace();}
  }

  @EventHandler(priority=EventPriority.HIGHEST)
  public void onQuit(PlayerQuitEvent event){
    PendingSign pending=pendingSigns.remove(event.getPlayer().getName());
    if(pending!=null){
      try{restoreSign(pending);}catch(Exception e){e.printStackTrace();}
    }
  }

  @EventHandler(priority=EventPriority.HIGHEST)
  public void onChat(AsyncPlayerChatEvent event){ String prompt=chatPrompts.remove(event.getPlayer().getName()); if(prompt==null)return; event.setCancelled(true); try { String[] parts=prompt.split("\\|",2); int id=Integer.parseInt(parts[0]); String kind=parts[1]; String value=event.getMessage(); Map<String,String> n=npcs.get(id); if(n==null){msg(event.getPlayer(),"&cNPC not found.");return;} if("name".equals(kind)){n.put("name",value); refreshStored(n); save(); msg(event.getPlayer(),"&aName updated.");} else if("message".equals(kind)){n.put("message",value);n.put("action","message");save();msg(event.getPlayer(),"&aMessage updated.");} else if("command".equals(kind)){n.put("command",value);n.put("action","command");save();msg(event.getPlayer(),"&aCommand updated.");} else if("action".equals(kind)){setAction(event.getPlayer(),new String[]{"action",""+id,value});} } catch(Exception e){e.printStackTrace();} }

  private void registerCommand(){ try { org.bukkit.command.PluginCommand c=getCommand("npcz"); if(c==null){getLogger().severe("Command /npcz is missing from plugin.yml!"); return;} c.setExecutor(this); } catch(Exception e){ e.printStackTrace(); } }
  @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args){ return command(sender, command, label, args); }
  private boolean command(Object sender,Object cmd,String label,String[] a){try{if(a.length==0||a[0].equalsIgnoreCase("help")){msg(sender,"&e/npcz create <mob> | edit <id> | remove <id> | list | reload");msg(sender,"&e/npcz name/action/message/command/setlocation <id> ... | setrtp <command>");return true;} switch(a[0].toLowerCase()){case "create": if(a.length<2){msg(sender,"&cUsage: /npcz create <mob>");return true;} return create(sender,a[1]);case "remove":return remove(sender,a);case "list":for(Map<String,String> n:npcs.values())msg(sender,"&e#"+n.get("id")+" &7"+n.get("type")+" &f"+n.get("name"));return true;case "edit":case "gui":if(a.length<2){msg(sender,"&cUsage: /npcz edit <id>");return true;} openGui(sender,Integer.parseInt(a[1]));return true;case "name":case "setname":if(a.length<3){msg(sender,"&cUsage: /npcz name <id> <name>");return true;}return setName(sender,a);case "action":if(a.length<3){msg(sender,"&cUsage: /npcz action <id> <message|teleport|rtp|command>");return true;}return setAction(sender,a);case "message":if(a.length<3){msg(sender,"&cUsage: /npcz message <id> <text>");return true;}return setMessage(sender,a);case "command":if(a.length<3){msg(sender,"&cUsage: /npcz command <id> <command>");return true;}return setCommand(sender,a);case "setlocation":case "location":if(a.length<2){msg(sender,"&cUsage: /npcz setlocation <id>");return true;}return setLocation(sender,a);case "reload":load();msg(sender,"&aNPCZ reloaded.");return true;case "setrtp":if(a.length<2){msg(sender,"&cUsage: /npcz setrtp <command>");return true;}rtpCommand=String.join(" ",Arrays.copyOfRange(a,1,a.length));save();msg(sender,"&aRTP command set to /"+rtpCommand);return true;default:msg(sender,"&cUnknown subcommand. Try /npcz help");return true;}}catch(Exception e){try{msg(sender,"&cError: "+e.getMessage());}catch(Exception ignored){}return true;}}
  private boolean create(Object sender,String type) throws Exception{Object loc=call(sender,"getLocation"); Object world=call(loc,"getWorld"); Class<?> et=Class.forName("org.bukkit.entity.EntityType"); Object t=Enum.valueOf((Class)et,type.toUpperCase()); Object ent=call(world,"spawnEntity",new Class[]{Class.forName("org.bukkit.Location"),et},loc,t); int id=nextId++; Map<String,String> n=new LinkedHashMap<>();n.put("id",""+id);n.put("type",type.toUpperCase());n.put("name",type);n.put("action","message");n.put("message","&eHello!");n.put("world",(String)call(world,"getName"));n.put("x",""+call(loc,"getX"));n.put("y",""+call(loc,"getY"));n.put("z",""+call(loc,"getZ"));n.put("yaw",""+call(loc,"getYaw"));n.put("pitch",""+call(loc,"getPitch"));npcs.put(id,n); configureEntity(ent,n);save();msg(sender,"&aCreated NPC #"+id+".");return true;}
  private boolean setName(Object sender,String[] a)throws Exception{int id=Integer.parseInt(a[1]);Map<String,String> n=npcs.get(id);if(n==null){msg(sender,"&cNPC not found.");return true;}n.put("name",String.join(" ",Arrays.copyOfRange(a,2,a.length)));refreshStored(n);save();msg(sender,"&aName updated for NPC #"+id+".");return true;}
  private boolean setAction(Object sender,String[] a)throws Exception{int id=Integer.parseInt(a[1]);Map<String,String> n=npcs.get(id);if(n==null){msg(sender,"&cNPC not found.");return true;}String v=a[2].toLowerCase();if(!(v.equals("message")||v.equals("teleport")||v.equals("rtp")||v.equals("command"))){msg(sender,"&cAction must be message, teleport, rtp, or command.");return true;}n.put("action",v);save();msg(sender,"&aNPC #"+id+" action set to "+v+".");return true;}
  private boolean setMessage(Object sender,String[] a)throws Exception{int id=Integer.parseInt(a[1]);Map<String,String> n=npcs.get(id);if(n==null){msg(sender,"&cNPC not found.");return true;}n.put("message",String.join(" ",Arrays.copyOfRange(a,2,a.length)));n.put("action","message");save();msg(sender,"&aMessage set for NPC #"+id+".");return true;}
  private boolean setCommand(Object sender,String[] a)throws Exception{int id=Integer.parseInt(a[1]);Map<String,String> n=npcs.get(id);if(n==null){msg(sender,"&cNPC not found.");return true;}n.put("command",String.join(" ",Arrays.copyOfRange(a,2,a.length)));n.put("action","command");save();msg(sender,"&aCommand set for NPC #"+id+".");return true;}
  private boolean setLocation(Object sender,String[] a)throws Exception{int id=Integer.parseInt(a[1]);Map<String,String> n=npcs.get(id);if(n==null){msg(sender,"&cNPC not found.");return true;}Object loc=call(sender,"getLocation");Object world=call(loc,"getWorld");n.put("world",""+call(world,"getName"));n.put("x",""+call(loc,"getX"));n.put("y",""+call(loc,"getY"));n.put("z",""+call(loc,"getZ"));n.put("yaw",""+call(loc,"getYaw"));n.put("pitch",""+call(loc,"getPitch"));save();msg(sender,"&aTeleport location set for NPC #"+id+".");return true;}
  private void refreshStored(Map<String,String> n)throws Exception{String u=n.get("uuid");if(u==null)return;for(Object w:(Collection<?>)call(server,"getWorlds"))for(Object e:(Collection<?>)call(w,"getEntities"))if(u.equals(""+call(e,"getUniqueId"))){configureEntity(e,n);return;}}
  private boolean remove(Object sender,String[] a)throws Exception{if(a.length<2){msg(sender,"&cUsage: /npcz remove <id>");return true;}int id=Integer.parseInt(a[1]);Map<String,String> n=npcs.remove(id);if(n==null){msg(sender,"&cNPC not found.");return true;}killStored(n);save();msg(sender,"&aRemoved NPC #"+id+".");return true;}
  private void handle(Object event){try{String cn=event.getClass().getName();if(cn.endsWith("PlayerInteractEntityEvent")){Object ent=call(event,"getRightClicked");Map<String,String> n=find(ent);if(n!=null){call(event,"setCancelled",new Class[]{boolean.class},true);Object p=call(event,"getPlayer");action(p,n);}}else if(cn.endsWith("EntityDamageEvent")||cn.endsWith("EntityExplodeEvent")||cn.endsWith("ExplosionPrimeEvent")||cn.endsWith("EntityCombustEvent")||cn.endsWith("EntityTargetEvent")){Object ent=call(event,"getEntity");if(find(ent)!=null)call(event,"setCancelled",new Class[]{boolean.class},true);}else if(cn.endsWith("InventoryClickEvent")){Object p=call(event,"getWhoClicked");Object view=call(event,"getView");String title=""+call(view,"getTitle");if(title.startsWith("NPCZ #")){call(event,"setCancelled",new Class[]{boolean.class},true);int id=Integer.parseInt(title.substring(6));int slot=(Integer)call(event,"getRawSlot");guiClick(p,id,slot);}}else if(cn.endsWith("InventoryDragEvent")){Object p=call(event,"getWhoClicked");Object view=call(event,"getView");if((""+call(view,"getTitle")).startsWith("NPCZ #"))call(event,"setCancelled",new Class[]{boolean.class},true);}}catch(Exception e){e.printStackTrace();}}
  private Map<String,String> find(Object ent)throws Exception{String uuid=""+call(ent,"getUniqueId");for(Map<String,String> n:npcs.values())if(uuid.equals(n.get("uuid")))return n;return null;}
  private void action(Object player,Map<String,String> n)throws Exception{String a=n.get("action");if("message".equals(a)){msg(player,n.get("message"));}else if("teleport".equals(a)){Object w=call(server,"getWorld",new Class[]{String.class},n.get("world"));Class<?> lc=Class.forName("org.bukkit.Location");Object loc=lc.getConstructor(Class.forName("org.bukkit.World"),double.class,double.class,double.class,float.class,float.class).newInstance(w,Double.parseDouble(n.get("x")),Double.parseDouble(n.get("y")),Double.parseDouble(n.get("z")),Float.parseFloat(n.getOrDefault("yaw","0")),Float.parseFloat(n.getOrDefault("pitch","0")));call(player,"teleport",new Class[]{lc},loc);}else if("rtp".equals(a)){String c=rtpCommand.replace("{player}",""+call(player,"getName"));dispatch(player,c);}else if("command".equals(a)){dispatch(player,n.get("command").replace("{player}",""+call(player,"getName")));}}
  private void dispatch(Object player,String command)throws Exception{if(command.startsWith("/"))command=command.substring(1);call(server,"dispatchCommand",new Class[]{Class.forName("org.bukkit.command.CommandSender"),String.class},player,command);}
  private void openGui(Object player,int id)throws Exception{Map<String,String> n=npcs.get(id);if(n==null){msg(player,"&cNPC not found.");return;}Object inv=call(server,"createInventory",new Class[]{Class.forName("org.bukkit.inventory.InventoryHolder"),int.class,String.class},null,9,"NPCZ #"+id);Class<?> mat=Class.forName("org.bukkit.Material");String[] names={"NAME","ACTION","TELEPORT","RTP","COMMAND","MESSAGE","SAVE","DELETE","INFO"};for(int i=0;i<9;i++){Object item=Class.forName("org.bukkit.inventory.ItemStack").getConstructor(mat).newInstance(Enum.valueOf((Class)mat,"PAPER"));Object meta=call(item,"getItemMeta");call(meta,"setDisplayName",new Class[]{String.class},"§e"+names[i]);call(item,"setItemMeta",new Class[]{Class.forName("org.bukkit.inventory.meta.ItemMeta")},meta);call(inv,"setItem",new Class[]{int.class,Class.forName("org.bukkit.inventory.ItemStack")},i,item);}call(player,"openInventory",new Class[]{Class.forName("org.bukkit.inventory.Inventory")},inv);}
  private void guiClick(Object p,int id,int slot)throws Exception{Map<String,String> n=npcs.get(id);if(n==null)return;String playerName=""+call(p,"getName");if(slot<0||slot>8)return;switch(slot){case 0:openSignInput(p,id,"name",n.getOrDefault("name",""));break;case 1:String current=n.getOrDefault("action","message");String next=current.equals("message")?"teleport":current.equals("teleport")?"rtp":current.equals("rtp")?"command":"message";n.put("action",next);save();msg(p,"&aAction changed to &f"+next+"&a.");break;case 2:setLocation(p,new String[]{"setlocation",""+id});break;case 3:n.put("action","rtp");save();msg(p,"&aAction set to RTP.");break;case 4:openSignInput(p,id,"command",n.getOrDefault("command",""));break;case 5:openSignInput(p,id,"message",n.getOrDefault("message",""));break;case 6:save();msg(p,"&aSaved.");break;case 7:remove(p,new String[]{"remove",""+id});break;default:msg(p,"&eNPC #"+id+" | "+n.get("type")+" | "+n.get("name")+" | "+n.get("action"));}}
  private static class PendingSign{
    final String player;
    final int id;
    final String kind;
    final Location location;
    final Material oldType;
    final byte oldData;
    PendingSign(String player,int id,String kind,Location location,Material oldType,byte oldData){
      this.player=player;this.id=id;this.kind=kind;this.location=location;this.oldType=oldType;this.oldData=oldData;
    }
  }

  private void openSignInput(Object player,int id,String kind,String current)throws Exception{
    Map<String,String> n=npcs.get(id);
    if(n==null){msg(player,"&cNPC not found.");return;}
    chatPrompts.remove(""+call(player,"getName"));
    String playerName=""+call(player,"getName");
    Object bukkitPlayer=player;
    Object locObj=call(player,"getLocation");
    Location base=(Location)locObj;
    Location signLoc=base.clone().add(0,2,0);
    Block block=signLoc.getBlock();

    PendingSign previous=pendingSigns.remove(playerName);
    if(previous!=null)restoreSign(previous);

    Material oldType=block.getType();
    byte oldData=block.getData();
    block.setType(Material.SIGN_POST);
    block.setData((byte)0);

    Sign sign=(Sign)block.getState();
    String[] lines=splitSignText(current);
    sign.setLine(0,lines[0]);
    sign.setLine(1,lines[1]);
    sign.setLine(2,lines[2]);
    sign.setLine(3,lines[3]);
    sign.update(true,false);

    PendingSign pending=new PendingSign(playerName,id,kind,signLoc,oldType,oldData);
    pendingSigns.put(playerName,pending);
    call(player,"closeInventory");
    if(!sendOpenSignPacket(bukkitPlayer,signLoc)){
      pendingSigns.remove(playerName);
      restoreSign(pending);
      msg(player,"&cThis server could not open the sign editor.");
      return;
    }
    msg(player,"&7Edit the sign, then click Done. Your entry will be saved.");
  }

  private String[] splitSignText(String value){
    String v=value==null?"":value;
    String[] out={"","","",""};
    for(int i=0;i<v.length() && i<60;i++){
      int line=Math.min(i/15,3);
      out[line]+=v.charAt(i);
    }
    return out;
  }

  private boolean sendOpenSignPacket(Object player,Location loc){
    try{
      Object handle=call(player,"getHandle");
      String version=call(server,"getClass").toString();
      String pkg=server.getClass().getPackage().getName();
      String nmsVersion=pkg.substring(pkg.lastIndexOf('.')+1);
      Class<?> bp=Class.forName("net.minecraft.server."+nmsVersion+".BlockPosition");
      Object pos=bp.getConstructor(int.class,int.class,int.class).newInstance(loc.getBlockX(),loc.getBlockY(),loc.getBlockZ());
      Class<?> packetClass=Class.forName("net.minecraft.server."+nmsVersion+".PacketPlayOutOpenSignEditor");
      Object packet=packetClass.getConstructor(bp).newInstance(pos);
      Field connectionField=null;
      Class<?> hc=handle.getClass();
      while(hc!=null && connectionField==null){
        try{connectionField=hc.getDeclaredField("playerConnection");}
        catch(NoSuchFieldException e){hc=hc.getSuperclass();}
      }
      if(connectionField==null)return false;
      connectionField.setAccessible(true);
      Object connection=connectionField.get(handle);
      Method send=null;
      for(Method m:connection.getClass().getMethods()){
        if(m.getName().equals("sendPacket") && m.getParameterCount()==1){send=m;break;}
      }
      if(send==null)return false;
      send.invoke(connection,packet);
      return true;
    }catch(Exception e){
      getLogger().warning("Could not open sign editor: "+e.getClass().getSimpleName()+": "+e.getMessage());
      return false;
    }
  }

  private void finishSignInput(Object player,PendingSign pending,String value)throws Exception{
    Map<String,String> n=npcs.get(pending.id);
    if(n==null){msg(player,"&cNPC not found.");return;}
    String v=value==null?"":value.trim();
    if("name".equals(pending.kind)){
      if(v.isEmpty()){msg(player,"&cName cannot be empty.");return;}
      n.put("name",v);
      refreshStored(n);
      save();
      msg(player,"&aName saved.");
    }else if("message".equals(pending.kind)){
      n.put("message",v);
      n.put("action","message");
      save();
      msg(player,"&aMessage saved.");
    }else if("command".equals(pending.kind)){
      if(v.startsWith("/"))v=v.substring(1);
      n.put("command",v);
      n.put("action","command");
      save();
      msg(player,"&aCommand saved.");
    }
  }

  private void restoreSign(PendingSign pending){
    try{
      Block block=pending.location.getBlock();
      block.setType(pending.oldType);
      block.setData(pending.oldData);
      block.getState().update(true,false);
    }catch(Exception e){e.printStackTrace();}
  }

  private void configureEntity(Object ent,Map<String,String> n)throws Exception{call(ent,"setCustomName",new Class[]{String.class},color(n.get("name")));call(ent,"setCustomNameVisible",new Class[]{boolean.class},true);try{call(ent,"setAI",new Class[]{boolean.class},false);}catch(Exception ignored){}try{call(ent,"setInvulnerable",new Class[]{boolean.class},true);}catch(Exception ignored){}try{call(ent,"setSilent",new Class[]{boolean.class},true);}catch(Exception ignored){}if(ent.getClass().getName().endsWith("Creeper")){try{call(ent,"setPowered",new Class[]{boolean.class},false);}catch(Exception ignored){}}n.put("uuid",""+call(ent,"getUniqueId"));}
  private void spawnStored(Map<String,String> n)throws Exception{String old=n.get("uuid");if(old!=null){for(Object w:(Collection<?>)call(server,"getWorlds"))for(Object e:(Collection<?>)call(w,"getEntities"))if(old.equals(""+call(e,"getUniqueId"))){configureEntity(e,n);return;}}Object w=call(server,"getWorld",new Class[]{String.class},n.get("world"));if(w==null)return;Object loc=Class.forName("org.bukkit.Location").getConstructor(Class.forName("org.bukkit.World"),double.class,double.class,double.class,float.class,float.class).newInstance(w,Double.parseDouble(n.get("x")),Double.parseDouble(n.get("y")),Double.parseDouble(n.get("z")),Float.parseFloat(n.getOrDefault("yaw","0")),Float.parseFloat(n.getOrDefault("pitch","0")));Object ent=call(w,"spawnEntity",new Class[]{Class.forName("org.bukkit.Location"),Class.forName("org.bukkit.entity.EntityType")},loc,Enum.valueOf((Class)Class.forName("org.bukkit.entity.EntityType"),n.get("type")));configureEntity(ent,n);}
  private void killStored(Map<String,String> n)throws Exception{String u=n.get("uuid");if(u==null)return;for(Object w:(Collection<?>)call(server,"getWorlds")){for(Object e:(Collection<?>)call(w,"getEntities")){if(u.equals(""+call(e,"getUniqueId"))){call(e,"remove");return;}}}}
  private void save(){try{PrintWriter out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(dataFile),StandardCharsets.UTF_8));out.println("rtpCommand="+escape(rtpCommand));out.println("nextId="+nextId);for(Map<String,String> n:npcs.values()){StringBuilder b=new StringBuilder("npc=");for(Map.Entry<String,String> e:n.entrySet())b.append(escape(e.getKey())).append("|").append(escape(e.getValue())).append(";");out.println(b);}out.close();}catch(Exception e){e.printStackTrace();}}
  private void load(){npcs.clear();nextId=1;try{if(!dataFile.exists())return;for(String l:Files.readAllLines(dataFile.toPath(),StandardCharsets.UTF_8)){if(l.startsWith("rtpCommand="))rtpCommand=unescape(l.substring(11));else if(l.startsWith("nextId="))nextId=Integer.parseInt(l.substring(7));else if(l.startsWith("npc=")){Map<String,String> n=new LinkedHashMap<>();for(String pair:l.substring(4).split(";")){if(pair.isEmpty())continue;String[] kv=pair.split("\\|",2);if(kv.length==2)n.put(unescape(kv[0]),unescape(kv[1]));}if(n.containsKey("id"))npcs.put(Integer.parseInt(n.get("id")),n);}}}catch(Exception e){e.printStackTrace();}}
  private String escape(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}private String unescape(String s){try{return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}catch(Exception e){return s;}}
  private String color(String s){return s==null?"":s.replace('&','§');}
  private void msg(Object sender,String s)throws Exception{call(sender,"sendMessage",new Class[]{String.class},color(s));}
  private Object call(Object o,String name,Object...args)throws Exception{return call(o,name,null,args);}private Object call(Object o,String name,Class<?>[] types,Object...args)throws Exception{if(o==null)throw new NullPointerException(name);Class<?> c=o.getClass();Method best=null;for(Method m:c.getMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){if(types==null||Arrays.equals(m.getParameterTypes(),types)){best=m;break;}}if(best==null)throw new NoSuchMethodException(c.getName()+"."+name);best.setAccessible(true);return best.invoke(o,args);}
}
package hiddenknight5.supportrewards;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ServerSupportRewards extends JavaPlugin {
    private final Random random = new Random();

    private enum Rarity {
        COMMON(60, "common"), UNCOMMON(25, "uncommon"), RARE(9, "rare"),
        VERY_RARE(5, "very-rare"), JACKPOT(1, "jackpot");
        final int weight; final String key;
        Rarity(int weight, String key) { this.weight = weight; this.key = key; }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        getLogger().info("ServerSupportRewards 1.0.0 enabled.");
        getLogger().info("EaglerHost trigger: supportreward {player}");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("supportreward")) return false;
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by the server console.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /supportreward <player>");
            return true;
        }
        Player player = Bukkit.getPlayerExact(args[0]);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "That player must be online to receive the reward.");
            return true;
        }
        grantRewards(player);
        return true;
    }

    private void grantRewards(Player player) {
        int min = Math.max(1, getConfig().getInt("min-rewards", 1));
        int max = Math.max(min, getConfig().getInt("max-rewards", 3));
        int rolls = min + random.nextInt(max - min + 1);
        List<String> descriptions = new ArrayList<String>();
        Rarity highest = Rarity.COMMON;
        boolean jackpot = false;

        for (int i = 0; i < rolls; i++) {
            Rarity rarity = rollRarity();
            if (rarity.ordinal() > highest.ordinal()) highest = rarity;
            if (rarity == Rarity.JACKPOT) {
                runJackpot(player);
                jackpot = true;
                descriptions.add("the SERVER SUPPORT JACKPOT");
            } else {
                descriptions.add(grantLoot(player, rarity));
            }
        }

        if (jackpot) {
            broadcast(getConfig().getString("messages.jackpot").replace("{player}", player.getName()));
        } else {
            String message = getConfig().getString("messages." + highest.key);
            message = message.replace("{player}", player.getName());
            message = message.replace("{rewards}", join(descriptions));
            broadcast(message);
        }
    }

    private Rarity rollRarity() {
        int n = random.nextInt(100) + 1, total = 0;
        for (Rarity rarity : Rarity.values()) {
            total += rarity.weight;
            if (n <= total) return rarity;
        }
        return Rarity.COMMON;
    }

    private String grantLoot(Player p, Rarity rarity) {
        switch (rarity) {
            case COMMON: return common(p);
            case UNCOMMON: return uncommon(p);
            case RARE: return rare(p);
            case VERY_RARE: return veryRare(p);
            default: return "Unknown reward";
        }
    }

    private String common(Player p) {
        int pick = random.nextInt(6);
        if (pick == 0) { int n=rand(2,5); give(p,new ItemStack(Material.DIAMOND,n)); return n+" Diamonds"; }
        if (pick == 1) { int n=rand(8,16); give(p,new ItemStack(Material.GOLD_INGOT,n)); return n+" Gold Ingots"; }
        if (pick == 2) { int n=rand(16,32); give(p,new ItemStack(Material.EMERALD,n)); return n+" Emeralds"; }
        if (pick == 3) { int n=rand(16,32); give(p,new ItemStack(Material.IRON_INGOT,n)); return n+" Iron Ingots"; }
        if (pick == 4) { int n=rand(16,32); give(p,new ItemStack(Material.REDSTONE,n)); return n+" Redstone"; }
        PotionEffectType[] e={PotionEffectType.SPEED,PotionEffectType.NIGHT_VISION,PotionEffectType.JUMP};
        PotionEffectType effect=e[random.nextInt(e.length)]; int minutes=rand(5,10);
        p.addPotionEffect(new PotionEffect(effect,minutes*60*20,0));
        return effectName(effect)+" for "+minutes+" minutes";
    }

    private String uncommon(Player p) {
        int pick=random.nextInt(5);
        if (pick==0) { int n=rand(5,10); give(p,new ItemStack(Material.DIAMOND,n)); return n+" Diamonds"; }
        if (pick==1) { ItemStack b=new ItemStack(Material.ENCHANTED_BOOK); b.addUnsafeEnchantment(Enchantment.DIG_SPEED,2); give(p,b); return "an Enchanted Book (Efficiency II)"; }
        if (pick==2) { give(p,new ItemStack(Material.GOLDEN_APPLE,1)); return "1 Golden Apple"; }
        if (pick==3) { int n=rand(16,32); give(p,new ItemStack(Material.GOLD_INGOT,n)); return n+" Gold Ingots"; }
        PotionEffectType[] e={PotionEffectType.FAST_DIGGING,PotionEffectType.FIRE_RESISTANCE,PotionEffectType.DAMAGE_RESISTANCE};
        PotionEffectType effect=e[random.nextInt(e.length)]; int minutes=rand(5,15);
        p.addPotionEffect(new PotionEffect(effect,minutes*60*20,0));
        return effectName(effect)+" for "+minutes+" minutes";
    }

    private String rare(Player p) {
        int pick=random.nextInt(5);
        if (pick==0) { int n=rand(10,20); give(p,new ItemStack(Material.DIAMOND,n)); return n+" Diamonds"; }
        if (pick==1) { int n=rand(1,3); give(p,new ItemStack(Material.GOLDEN_APPLE,n)); return n+" Golden Apple"+(n==1?"":"s"); }
        if (pick==2) { int n=rand(1,2); give(p,witherSkull(n)); return n+" Wither Skeleton Skull"+(n==1?"":"s"); }
        if (pick==3) { int n=rand(16,32); give(p,new ItemStack(Material.GOLD_INGOT,n)); return n+" Gold Ingots"; }
        PotionEffectType[] e={PotionEffectType.INCREASE_DAMAGE,PotionEffectType.SPEED,PotionEffectType.DAMAGE_RESISTANCE,PotionEffectType.FAST_DIGGING};
        PotionEffectType effect=e[random.nextInt(e.length)]; int minutes=rand(10,20);
        p.addPotionEffect(new PotionEffect(effect,minutes*60*20,0));
        return effectName(effect)+" for "+minutes+" minutes";
    }

    private String veryRare(Player p) {
        if (random.nextBoolean()) {
            int n=rand(1,2); give(p,witherSkull(n)); return n+" Wither Skeleton Skull"+(n==1?"":"s");
        }
        ItemStack b=new ItemStack(Material.ENCHANTED_BOOK);
        b.addUnsafeEnchantment(Enchantment.DAMAGE_ALL,4);
        b.addUnsafeEnchantment(Enchantment.DURABILITY,3);
        give(p,b);
        return "an Enchanted Book (Sharpness IV + Unbreaking III)";
    }

    private void runJackpot(Player player) {
        String command=getConfig().getString("jackpot-command","");
        if (command==null || command.trim().isEmpty() || command.contains("PUT-YOUR-JACKPOT-COMMAND-HERE")) {
            getLogger().warning("Jackpot triggered for "+player.getName()+" but jackpot-command is not configured.");
            return;
        }
        command=command.replace("{player}",player.getName());
        if (command.startsWith("/")) command=command.substring(1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command);
    }

    private ItemStack witherSkull(int amount) {
        return new ItemStack(Material.SKULL_ITEM,amount,(short)1);
    }

    private void give(Player player,ItemStack item) {
        Map<Integer,ItemStack> leftover=player.getInventory().addItem(item);
        for (ItemStack stack:leftover.values()) player.getWorld().dropItemNaturally(player.getLocation(),stack);
    }

    private int rand(int min,int max) { return min+random.nextInt(max-min+1); }

    private String effectName(PotionEffectType type) {
        if (type==PotionEffectType.SPEED) return "Speed";
        if (type==PotionEffectType.NIGHT_VISION) return "Night Vision";
        if (type==PotionEffectType.JUMP) return "Jump Boost";
        if (type==PotionEffectType.FAST_DIGGING) return "Haste";
        if (type==PotionEffectType.FIRE_RESISTANCE) return "Fire Resistance";
        if (type==PotionEffectType.DAMAGE_RESISTANCE) return "Resistance";
        if (type==PotionEffectType.INCREASE_DAMAGE) return "Strength";
        return type.getName();
    }

    private String join(List<String> rewards) {
        if (rewards.size()==1) return rewards.get(0);
        if (rewards.size()==2) return rewards.get(0)+" and "+rewards.get(1);
        StringBuilder b=new StringBuilder();
        for(int i=0;i<rewards.size();i++){ if(i>0)b.append(i==rewards.size()-1?", and ": ", "); b.append(rewards.get(i)); }
        return b.toString();
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',message));
    }
}
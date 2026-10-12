package hiddenknight5.deathchest;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class DeathChestPlugin extends JavaPlugin implements Listener {
    private final Map<String, DeathChest> chests = new LinkedHashMap<String, DeathChest>();
    private File dataFile;
    private YamlConfiguration data;
    private int revealMinutes, xpPerBottle;

    @Override public void onEnable() {
        saveDefaultConfig();
        revealMinutes = Math.max(1, getConfig().getInt("reveal-time-minutes", 10));
        xpPerBottle = Math.max(1, getConfig().getInt("xp-points-per-bottle", 3));
        dataFile = new File(getDataFolder(), "deathchests.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadChests();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { tick(); }
        }, 20L, 20L);
    }

    @Override public void onDisable() { saveChests(); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        // The event drop list is the single source of truth: it already represents
        // what Bukkit intends to drop, including armor/offhand where supported.
        List<ItemStack> loot = new ArrayList<ItemStack>();
        for (ItemStack item : event.getDrops()) {
            if (valid(item)) loot.add(item.clone());
        }
        int droppedXp = Math.max(0, event.getDroppedExp());
        int bottleCount = droppedXp / xpPerBottle;
        for (int i = 0; i < bottleCount; i++) loot.add(new ItemStack(Material.EXP_BOTTLE, 1));

        if (loot.isEmpty()) return;
        Location base = findLocation(player.getLocation());
        if (base == null) {
            player.sendMessage(msg("no-space"));
            return; // leave Bukkit's original drops and XP alone
        }

        Block first = base.getBlock();
        Block second = base.clone().add(1, 0, 0).getBlock();
        first.setType(Material.CHEST);
        second.setType(Material.CHEST);
        // Let the server update the paired chest before obtaining its combined inventory.
        Inventory inv = ((Chest) first.getState()).getBlockInventory();
        // The combined inventory is obtained from the block state after the pair exists.
        inv = ((Chest) first.getState()).getInventory();

        event.getDrops().clear();
        event.setDroppedExp(0);
        // Do not separately copy player inventory, armor, or offhand. Clearing the
        // event drop list prevents Bukkit from dropping the same items a second time.
        Map<Integer, ItemStack> overflow = inv.addItem(loot.toArray(new ItemStack[loot.size()]));
        for (ItemStack item : overflow.values()) {
            first.getWorld().dropItemNaturally(base.clone().add(0.5, 0.5, 0.5), item);
        }

        long deadline = System.currentTimeMillis() + revealMinutes * 60_000L;
        DeathChest dc = new DeathChest(player.getUniqueId(), player.getName(),
                first.getWorld().getName(), first.getX(), first.getY(), first.getZ(),
                second.getX(), second.getY(), second.getZ(), deadline, false);
        chests.put(dc.key(), dc);
        saveChests();
        player.sendMessage(formatMessage("private-location", dc));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        // Run one tick later so login plugins/messages do not race the reminder.
        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override public void run() {
                for (DeathChest dc : new ArrayList<DeathChest>(chests.values())) {
                    if (dc.owner.equals(player.getUniqueId()) && exists(dc)) {
                        player.sendMessage(formatMessage("private-location", dc));
                    }
                }
            }
        });
    }

    @EventHandler public void onBreak(BlockBreakEvent event) {
        for (DeathChest dc : chests.values()) {
            if (dc.matches(event.getBlock())) { event.setCancelled(true); return; }
        }
    }

    @EventHandler public void onExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            for (DeathChest dc : chests.values()) if (dc.matches(block)) { it.remove(); break; }
        }
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        for (DeathChest dc : new ArrayList<DeathChest>(chests.values())) {
            if (!exists(dc)) continue;
            Inventory inv = combinedInventory(dc);
            if (inv != null && inv.getViewers().isEmpty() && isEmpty(inv)) {
                remove(dc);
                if (event.getPlayer() instanceof Player) {
                    Player p = (Player) event.getPlayer();
                    if (dc.owner.equals(p.getUniqueId())) p.sendMessage(color(getConfig().getString("messages.claimed", "&aYour death chest has been claimed.")));
                }
            }
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (DeathChest dc : new ArrayList<DeathChest>(chests.values())) {
            if (!exists(dc)) { chests.remove(dc.key()); saveChests(); continue; }
            Inventory inv = combinedInventory(dc);
            if (inv != null && isEmpty(inv) && inv.getViewers().isEmpty()) { remove(dc); continue; }
            if (!dc.revealed && now >= dc.deadline) {
                dc.revealed = true;
                Bukkit.broadcastMessage(formatMessage("revealed", dc));
                saveChests();
            } else if (!dc.revealed && now < dc.deadline && now / 60000L != dc.lastMinute) {
                dc.lastMinute = now / 60000L;
                Player owner = Bukkit.getPlayer(dc.owner);
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage(color(getConfig().getString("messages.countdown", "&eYour death chest will become public in &e{time}&f.")
                        .replace("{time}", format(dc.deadline - now))));
                }
            }
        }
    }

    private Inventory combinedInventory(DeathChest dc) {
        World w = Bukkit.getWorld(dc.world);
        if (w == null) return null;
        Block b = w.getBlockAt(dc.x1, dc.y1, dc.z1);
        if (!(b.getState() instanceof Chest)) return null;
        return ((Chest) b.getState()).getInventory();
    }

    private boolean isEmpty(Inventory inv) {
        for (ItemStack item : inv.getContents()) if (valid(item)) return false;
        return true;
    }

    private void remove(DeathChest dc) {
        World w = Bukkit.getWorld(dc.world);
        if (w != null) {
            Block a = w.getBlockAt(dc.x1, dc.y1, dc.z1);
            Block b = w.getBlockAt(dc.x2, dc.y2, dc.z2);
            if (a.getType() == Material.CHEST) a.setType(Material.AIR);
            if (b.getType() == Material.CHEST) b.setType(Material.AIR);
        }
        chests.remove(dc.key());
        saveChests();
    }

    private Location findLocation(Location origin) {
        World w = origin.getWorld();
        int ox = origin.getBlockX(), oy = origin.getBlockY(), oz = origin.getBlockZ();
        for (int r = 0; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    int x = ox + dx, y = oy + dy, z = oz + dz;
                    if (y < 1 || y >= w.getMaxHeight() - 1) continue;
                    Block a = w.getBlockAt(x, y, z), b = w.getBlockAt(x + 1, y, z);
                    if (a.getType() != Material.AIR || b.getType() != Material.AIR) continue;
                    if (a.getRelative(0, -1, 0).isLiquid() || b.getRelative(0, -1, 0).isLiquid()) continue;
                    if (a.getRelative(0, -1, 0).getType() == Material.AIR || b.getRelative(0, -1, 0).getType() == Material.AIR) continue;
                    return new Location(w, x, y, z);
                }
            }
        }
        return null;
    }

    private boolean exists(DeathChest dc) {
        World w = Bukkit.getWorld(dc.world);
        return w != null && w.getBlockAt(dc.x1, dc.y1, dc.z1).getType() == Material.CHEST
            && w.getBlockAt(dc.x2, dc.y2, dc.z2).getType() == Material.CHEST;
    }

    private boolean valid(ItemStack i) { return i != null && i.getType() != Material.AIR && i.getAmount() > 0; }
    private String formatMessage(String path, DeathChest dc) {
        return color(getConfig().getString("messages." + path, "Your death chest is at {x} {y} {z}")
            .replace("{x}", String.valueOf(dc.x1)).replace("{y}", String.valueOf(dc.y1))
            .replace("{z}", String.valueOf(dc.z1)).replace("{player}", dc.name));
    }
    private String msg(String key) { return color(getConfig().getString("messages." + key, "Your death chest could not be placed.")); }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private String format(long ms) {
        long seconds = Math.max(0, ms / 1000L);
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    private void loadChests() {
        if (!data.isConfigurationSection("chests")) return;
        for (String k : data.getConfigurationSection("chests").getKeys(false)) {
            String p = "chests." + k;
            try {
                UUID owner = UUID.fromString(data.getString(p + ".owner"));
                String worldValue = data.getString(p + ".world");
                World world = null;
                try { world = Bukkit.getWorld(UUID.fromString(worldValue)); } catch (Exception ignored) {}
                if (world == null) world = Bukkit.getWorld(worldValue);
                if (world == null) continue;
                int x1 = data.contains(p + ".x1") ? data.getInt(p + ".x1") : data.getInt(p + ".x");
                int y1 = data.contains(p + ".y1") ? data.getInt(p + ".y1") : data.getInt(p + ".y");
                int z1 = data.contains(p + ".z1") ? data.getInt(p + ".z1") : data.getInt(p + ".z");
                int x2 = data.getInt(p + ".x2"), y2 = data.contains(p + ".y2") ? data.getInt(p + ".y2") : y1;
                int z2 = data.contains(p + ".z2") ? data.getInt(p + ".z2") : z1;
                String name = data.getString(p + ".name", data.getString(p + ".player", "Unknown"));
                DeathChest dc = new DeathChest(owner, name, world.getName(), x1, y1, z1, x2, y2, z2,
                    data.getLong(p + ".deadline"), data.getBoolean(p + ".revealed", false));
                if (exists(dc)) chests.put(dc.key(), dc);
            } catch (Exception ex) { getLogger().warning("Skipping invalid saved death chest: " + k); }
        }
    }
    private void saveChests() {
        data = new YamlConfiguration();
        for (DeathChest dc : chests.values()) {
            String p = "chests." + dc.key();
            data.set(p + ".owner", dc.owner.toString()); data.set(p + ".name", dc.name);
            data.set(p + ".world", dc.world); data.set(p + ".x1", dc.x1); data.set(p + ".y1", dc.y1); data.set(p + ".z1", dc.z1);
            data.set(p + ".x2", dc.x2); data.set(p + ".y2", dc.y2); data.set(p + ".z2", dc.z2);
            data.set(p + ".deadline", dc.deadline); data.set(p + ".revealed", dc.revealed);
        }
        try { if (!getDataFolder().exists()) getDataFolder().mkdirs(); data.save(dataFile); }
        catch (IOException e) { getLogger().severe("Could not save death chests: " + e.getMessage()); }
    }

    private static class DeathChest {
        final UUID owner; final String name, world; final int x1,y1,z1,x2,y2,z2; final long deadline;
        boolean revealed; long lastMinute = -1;
        DeathChest(UUID owner, String name, String world, int x1, int y1, int z1, int x2, int y2, int z2, long deadline, boolean revealed) {
            this.owner=owner; this.name=name; this.world=world; this.x1=x1; this.y1=y1; this.z1=z1; this.x2=x2; this.y2=y2; this.z2=z2; this.deadline=deadline; this.revealed=revealed;
        }
        String key() { return world + "_" + x1 + "_" + y1 + "_" + z1; }
        boolean matches(Block b) { return b.getWorld().getName().equals(world) && ((b.getX()==x1&&b.getY()==y1&&b.getZ()==z1)||(b.getX()==x2&&b.getY()==y2&&b.getZ()==z2)); }
    }
}
package com.hiddenknight5.npcz;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class NPCZPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<Integer, Map<String, String>> npcs = new LinkedHashMap<>();
    private File dataFile;
    private int nextId = 1;

    // Built-in RTP settings.
    // A negative value means RTP has not been configured yet.
    private int rtpMin = -1;
    private int rtpMax = -1;

    @Override
    public void onEnable() {
        try {
            dataFile = new File(getDataFolder(), "npcs.yml");
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }

            load();
            getServer().getPluginManager().registerEvents(this, this);

            if (getCommand("npcz") != null) {
                getCommand("npcz").setExecutor(this);
            }
            if (getCommand("rtp") != null) {
                getCommand("rtp").setExecutor(this);
            }
            if (getCommand("rtpsetup") != null) {
                getCommand("rtpsetup").setExecutor(this);
            }

            for (Map<String, String> npc : npcs.values()) {
                spawnStored(npc);
            }

            getLogger().info("NPCZ enabled. Built-in RTP: "
                    + (rtpConfigured() ? (rtpMin + "-" + rtpMax + " blocks") : "not configured"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        save();
    }

    // -------------------------
    // NPC protection / interaction
    // -------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        try {
            Map<String, String> npc = find(event.getRightClicked());
            if (npc == null) {
                return;
            }

            event.setCancelled(true);
            action(event.getPlayer(), npc);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcDamage(EntityDamageEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcExplode(EntityExplodeEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcPrime(ExplosionPrimeEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcCombust(EntityCombustEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcTarget(EntityTargetEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private Map<String, String> find(Entity entity) {
        String uuid = entity.getUniqueId().toString();
        for (Map<String, String> npc : npcs.values()) {
            if (uuid.equals(npc.get("uuid"))) {
                return npc;
            }
        }
        return null;
    }

    private boolean isNpc(Entity entity) {
        return find(entity) != null;
    }

    // -------------------------
    // Commands
    // -------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        try {
            if (name.equals("rtp")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(color("&c/rtp can only be used by a player."));
                    return true;
                }

                Player player = (Player) sender;

                if (!rtpConfigured()) {
                    player.sendMessage(color("&cRTP is not configured yet. Use &f/rtpsetup <min> <max>&c."));
                    return true;
                }

                player.sendMessage(color("&7Finding a safe random location..."));
                if (!teleportRandom(player)) {
                    player.sendMessage(color("&cCould not find a safe RTP location. Try again."));
                }
                return true;
            }

            if (name.equals("rtpsetup")) {
                if (!sender.hasPermission("npcz.admin")) {
                    sender.sendMessage(color("&cYou do not have permission."));
                    return true;
                }

                if (args.length != 2) {
                    sender.sendMessage(color("&cUsage: /rtpsetup <min> <max>"));
                    sender.sendMessage(color("&7Example: /rtpsetup 100 1000"));
                    return true;
                }

                int min;
                int max;

                try {
                    min = Integer.parseInt(args[0]);
                    max = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(color("&cBoth values must be whole numbers."));
                    return true;
                }

                if (min < 0) {
                    sender.sendMessage(color("&cMinimum radius cannot be negative."));
                    return true;
                }

                if (max <= 0) {
                    sender.sendMessage(color("&cMaximum radius must be greater than 0."));
                    return true;
                }

                if (min >= max) {
                    sender.sendMessage(color("&cMinimum radius must be smaller than maximum radius."));
                    return true;
                }

                // Keep the values reasonable for a 1.12.2 server.
                if (max > 29_000_000) {
                    sender.sendMessage(color("&cMaximum radius is too large. Keep it at or below 29,000,000."));
                    return true;
                }

                rtpMin = min;
                rtpMax = max;
                save();

                sender.sendMessage(color("&aBuilt-in RTP range set to &f" + rtpMin + " &ato &f" + rtpMax + "&a blocks."));
                sender.sendMessage(color("&7Players can now use &f/rtp&7."));
                return true;
            }

            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                sender.sendMessage(color("&e/npcz create <mob>"));
                sender.sendMessage(color("&e/npcz remove <id>"));
                sender.sendMessage(color("&e/npcz list"));
                sender.sendMessage(color("&e/npcz name <id> <name>"));
                sender.sendMessage(color("&e/npcz action <id> <message|teleport|rtp|command>"));
                sender.sendMessage(color("&e/npcz message <id> <text>"));
                sender.sendMessage(color("&e/npcz command <id> <command>"));
                sender.sendMessage(color("&e/npcz setlocation <id>"));
                sender.sendMessage(color("&e/rtpsetup <min> <max>"));
                sender.sendMessage(color("&e/rtp"));
                return true;
            }

            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create":
                    if (args.length < 2) {
                        sender.sendMessage(color("&cUsage: /npcz create <mob>"));
                        return true;
                    }
                    return create(sender, args[1]);

                case "remove":
                    return remove(sender, args);

                case "list":
                    if (npcs.isEmpty()) {
                        sender.sendMessage(color("&7No NPCs exist."));
                        return true;
                    }

                    for (Map<String, String> npc : npcs.values()) {
                        sender.sendMessage(color("&e#" + npc.get("id")
                                + " &7" + npc.get("type")
                                + " &f" + npc.get("name")
                                + " &8[" + npc.get("action") + "]"));
                    }
                    return true;

                case "name":
                case "setname":
                    if (args.length < 3) {
                        sender.sendMessage(color("&cUsage: /npcz name <id> <name>"));
                        return true;
                    }
                    return setName(sender, args);

                case "action":
                    if (args.length < 3) {
                        sender.sendMessage(color("&cUsage: /npcz action <id> <message|teleport|rtp|command>"));
                        return true;
                    }
                    return setAction(sender, args);

                case "message":
                    if (args.length < 3) {
                        sender.sendMessage(color("&cUsage: /npcz message <id> <text>"));
                        return true;
                    }
                    return setMessage(sender, args);

                case "command":
                    if (args.length < 3) {
                        sender.sendMessage(color("&cUsage: /npcz command <id> <command>"));
                        return true;
                    }
                    return setCommand(sender, args);

                case "setlocation":
                case "location":
                    if (args.length < 2) {
                        sender.sendMessage(color("&cUsage: /npcz setlocation <id>"));
                        return true;
                    }
                    return setLocation(sender, args);

                case "reload":
                    load();
                    for (Map<String, String> npc : npcs.values()) {
                        spawnStored(npc);
                    }
                    sender.sendMessage(color("&aNPCZ reloaded."));
                    return true;

                default:
                    sender.sendMessage(color("&cUnknown subcommand. Try /npcz help"));
                    return true;
            }
        } catch (Exception e) {
            sender.sendMessage(color("&cError: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
            getLogger().warning("Command error: " + e.getClass().getName() + ": " + e.getMessage());
            return true;
        }
    }

    // -------------------------
    // NPC creation / editing
    // -------------------------

    private boolean create(CommandSender sender, String typeName) throws Exception {
        EntityType type;

        try {
            type = EntityType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(color("&cUnknown entity type: &f" + typeName));
            return true;
        }

        if (!type.isSpawnable() || type.getEntityClass() == null) {
            sender.sendMessage(color("&cThat entity type cannot be used as an NPC."));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(color("&cNPCs can only be created by a player."));
            return true;
        }

        Player player = (Player) sender;
        Entity entity = player.getWorld().spawnEntity(player.getLocation(), type);

        int id = nextId++;

        Map<String, String> npc = new LinkedHashMap<>();
        npc.put("id", String.valueOf(id));
        npc.put("type", type.name());
        npc.put("name", type.name().toLowerCase(Locale.ROOT));
        npc.put("action", "message");
        npc.put("message", "&eHello!");
        saveLocation(npc, player.getLocation());

        npcs.put(id, npc);
        configureEntity(entity, npc);
        save();

        sender.sendMessage(color("&aCreated NPC #" + id + "."));
        return true;
    }

    private boolean setName(CommandSender sender, String[] args) throws Exception {
        int id = parseId(sender, args[1]);
        if (id < 0) return true;

        Map<String, String> npc = npcs.get(id);
        if (npc == null) {
            sender.sendMessage(color("&cNPC not found."));
            return true;
        }

        npc.put("name", String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        refreshStored(npc);
        save();

        sender.sendMessage(color("&aName updated for NPC #" + id + "."));
        return true;
    }

    private boolean setAction(CommandSender sender, String[] args) throws Exception {
        int id = parseId(sender, args[1]);
        if (id < 0) return true;

        Map<String, String> npc = npcs.get(id);
        if (npc == null) {
            sender.sendMessage(color("&cNPC not found."));
            return true;
        }

        String action = args[2].toLowerCase(Locale.ROOT);
        if (!action.equals("message")
                && !action.equals("teleport")
                && !action.equals("rtp")
                && !action.equals("command")) {
            sender.sendMessage(color("&cAction must be message, teleport, rtp, or command."));
            return true;
        }

        if (action.equals("rtp") && !rtpConfigured()) {
            sender.sendMessage(color("&eWarning: RTP is not configured yet. Use &f/rtpsetup <min> <max>&e."));
        }

        npc.put("action", action);
        save();

        sender.sendMessage(color("&aNPC #" + id + " action set to " + action + "."));
        return true;
    }

    private boolean setMessage(CommandSender sender, String[] args) throws Exception {
        int id = parseId(sender, args[1]);
        if (id < 0) return true;

        Map<String, String> npc = npcs.get(id);
        if (npc == null) {
            sender.sendMessage(color("&cNPC not found."));
            return true;
        }

        npc.put("message", String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        npc.put("action", "message");
        save();

        sender.sendMessage(color("&aMessage set for NPC #" + id + "."));
        return true;
    }

    private boolean setCommand(CommandSender sender, String[] args) throws Exception {
        int id = parseId(sender, args[1]);
        if (id < 0) return true;

        Map<String, String> npc = npcs.get(id);
        if (npc == null) {
            sender.sendMessage(color("&cNPC not found."));
            return true;
        }

        String command = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        npc.put("command", command);
        npc.put("action", "command");
        save();

        sender.sendMessage(color("&aCommand set for NPC #" + id + "."));
        return true;
    }

    private boolean setLocation(CommandSender sender, String[] args) throws Exception {
        int id = parseId(sender, args[1]);
        if (id < 0) return true;

        Map<String, String> npc = npcs.get(id);
        if (npc == null) {
            sender.sendMessage(color("&cNPC not found."));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(color("&cThis command must be used by a player."));
            return true;
        }

        saveLocation(npc, ((Player) sender).getLocation());
        save();

        sender.sendMessage(color("&aTeleport location set for NPC #" + id + "."));
        return true;
    }

    private boolean remove(CommandSender sender, String[] args) throws Exception {
        if (args.length < 2) {
            sender.sendMessage(color("&cUsage: /npcz remove <id>"));
            return true;
        }

        int id = parseId(sender, args[1]);
        if (id < 0) return true;

        Map<String, String> npc = npcs.remove(id);
        if (npc == null) {
            sender.sendMessage(color("&cNPC not found."));
            return true;
        }

        killStored(npc);
        save();

        sender.sendMessage(color("&aRemoved NPC #" + id + "."));
        return true;
    }

    private int parseId(CommandSender sender, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            sender.sendMessage(color("&cNPC ID must be a number."));
            return -1;
        }
    }

    private void action(Player player, Map<String, String> npc) throws Exception {
        String action = npc.get("action");

        if ("message".equals(action)) {
            player.sendMessage(color(npc.getOrDefault("message", "")));
            return;
        }

        if ("teleport".equals(action)) {
            Location location = readLocation(npc);
            if (location == null) {
                player.sendMessage(color("&cNPC teleport location is invalid."));
                return;
            }
            player.teleport(location);
            return;
        }

        if ("rtp".equals(action)) {
            if (!rtpConfigured()) {
                player.sendMessage(color("&cRTP is not configured. An administrator must use &f/rtpsetup <min> <max>&c."));
                return;
            }

            player.sendMessage(color("&7Finding a safe random location..."));
            if (!teleportRandom(player)) {
                player.sendMessage(color("&cCould not find a safe RTP location. Try again."));
            }
            return;
        }

        if ("command".equals(action)) {
            String command = npc.getOrDefault("command", "");
            command = command.replace("{player}", player.getName());
            dispatchCommand(player, command);
        }
    }

    private void configureEntity(Entity entity, Map<String, String> npc) {
        if (!(entity instanceof LivingEntity)) {
            npc.put("uuid", entity.getUniqueId().toString());
            return;
        }

        LivingEntity living = (LivingEntity) entity;

        living.setCustomName(color(npc.getOrDefault("name", "")));
        living.setCustomNameVisible(true);
        living.setRemoveWhenFarAway(false);
        living.setCanPickupItems(false);
        living.setAI(false);
        living.setInvulnerable(true);
        living.setSilent(true);

        if (entity.getType() == EntityType.CREEPER) {
            org.bukkit.entity.Creeper creeper = (org.bukkit.entity.Creeper) entity;
            creeper.setPowered(false);
            creeper.setMaxFuseTicks(Integer.MAX_VALUE);
            creeper.setExplosionRadius(0);
        }

        npc.put("uuid", entity.getUniqueId().toString());
    }

    private void refreshStored(Map<String, String> npc) {
        String uuid = npc.get("uuid");
        if (uuid == null) return;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (uuid.equals(entity.getUniqueId().toString())) {
                    configureEntity(entity, npc);
                    return;
                }
            }
        }
    }

    private void spawnStored(Map<String, String> npc) {
        try {
            String uuid = npc.get("uuid");

            if (uuid != null) {
                for (World world : Bukkit.getWorlds()) {
                    for (Entity entity : world.getEntities()) {
                        if (uuid.equals(entity.getUniqueId().toString())) {
                            configureEntity(entity, npc);
                            return;
                        }
                    }
                }
            }

            Location location = readLocation(npc);
            if (location == null) {
                getLogger().warning("Could not spawn NPC #" + npc.get("id") + ": invalid location.");
                return;
            }

            EntityType type = EntityType.valueOf(npc.get("type"));
            Entity entity = location.getWorld().spawnEntity(location, type);
            configureEntity(entity, npc);
        } catch (Exception e) {
            getLogger().warning("Could not spawn NPC #" + npc.get("id") + ": " + e.getMessage());
        }
    }

    private void killStored(Map<String, String> npc) {
        String uuid = npc.get("uuid");
        if (uuid == null) return;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (uuid.equals(entity.getUniqueId().toString())) {
                    entity.remove();
                    return;
                }
            }
        }
    }

    // -------------------------
    // Built-in RTP
    // -------------------------

    private boolean rtpConfigured() {
        return rtpMin >= 0 && rtpMax > rtpMin;
    }

    private boolean teleportRandom(Player player) {
        if (!rtpConfigured()) {
            return false;
        }

        World world = player.getWorld();
        Location origin = player.getLocation();

        // 50 attempts gives plenty of chances without potentially hanging a server.
        for (int attempt = 0; attempt < 50; attempt++) {
            double radius = randomRadius(rtpMin, rtpMax);
            double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);

            int x = origin.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = origin.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);

            if (x < -29999900 || x > 29999900 || z < -29999900 || z > 29999900) {
                continue;
            }

            Location target = findSafeLocation(world, x, z);
            if (target == null) {
                continue;
            }

            // Do not RTP directly into the void or an unloaded/invalid area.
            if (target.getY() <= 1 || target.getY() >= world.getMaxHeight() - 2) {
                continue;
            }

            player.teleport(target);
            player.setFallDistance(0.0F);
            return true;
        }

        return false;
    }

    private double randomRadius(int min, int max) {
        // sqrt distribution gives a more even spread over the area of the annulus.
        double minSquared = (double) min * (double) min;
        double maxSquared = (double) max * (double) max;
        return Math.sqrt(ThreadLocalRandom.current().nextDouble(minSquared, maxSquared));
    }

    private Location findSafeLocation(World world, int x, int z) {
        int highestY;

        try {
            highestY = world.getHighestBlockYAt(x, z);
        } catch (Exception e) {
            return null;
        }

        // Search downward in case the highest block is leaves, snow, etc.
        for (int y = Math.min(highestY, world.getMaxHeight() - 3); y >= 1; y--) {
            Block ground = world.getBlockAt(x, y, z);
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);

            if (!isSafeGround(ground)) {
                continue;
            }

            if (!isAirLike(feet) || !isAirLike(head)) {
                continue;
            }

            // Extra check for liquid immediately around the feet.
            if (feet.isLiquid() || head.isLiquid()) {
                continue;
            }

            Location location = new Location(
                    world,
                    x + 0.5,
                    y + 1.0,
                    z + 0.5,
                    ThreadLocalRandom.current().nextFloat() * 360.0F,
                    0.0F
            );

            return location;
        }

        return null;
    }

    private boolean isSafeGround(Block block) {
        Material material = block.getType();

        if (block.isLiquid()) {
            return false;
        }

        if (!material.isSolid()) {
            return false;
        }

        switch (material) {
            case CACTUS:
            case FIRE:
            case BED:
            case TNT:
            case MAGMA:
            case WEB:
            case LEAVES:
            case LEAVES_2:
                return false;
            default:
                return true;
        }
    }

    private boolean isAirLike(Block block) {
        Material material = block.getType();
        return material == Material.AIR;
    }

    // -------------------------
    // Location / persistence
    // -------------------------

    private void saveLocation(Map<String, String> npc, Location location) {
        npc.put("world", location.getWorld().getName());
        npc.put("x", String.valueOf(location.getX()));
        npc.put("y", String.valueOf(location.getY()));
        npc.put("z", String.valueOf(location.getZ()));
        npc.put("yaw", String.valueOf(location.getYaw()));
        npc.put("pitch", String.valueOf(location.getPitch()));
    }

    private Location readLocation(Map<String, String> npc) {
        try {
            World world = Bukkit.getWorld(npc.get("world"));
            if (world == null) return null;

            return new Location(
                    world,
                    Double.parseDouble(npc.get("x")),
                    Double.parseDouble(npc.get("y")),
                    Double.parseDouble(npc.get("z")),
                    Float.parseFloat(npc.getOrDefault("yaw", "0")),
                    Float.parseFloat(npc.getOrDefault("pitch", "0"))
            );
        } catch (Exception e) {
            return null;
        }
    }

    private void dispatchCommand(Player player, String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }

        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        Bukkit.dispatchCommand(player, command);
    }

    private void save() {
        try {
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(dataFile),
                            StandardCharsets.UTF_8
                    )
            );

            out.println("rtpMin=" + rtpMin);
            out.println("rtpMax=" + rtpMax);
            out.println("nextId=" + nextId);

            for (Map<String, String> npc : npcs.values()) {
                StringBuilder line = new StringBuilder("npc=");

                for (Map.Entry<String, String> entry : npc.entrySet()) {
                    line.append(escape(entry.getKey()))
                            .append("|")
                            .append(escape(entry.getValue()))
                            .append(";");
                }

                out.println(line);
            }

            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void load() {
        npcs.clear();
        nextId = 1;
        rtpMin = -1;
        rtpMax = -1;

        try {
            if (!dataFile.exists()) {
                return;
            }

            for (String line : Files.readAllLines(dataFile.toPath(), StandardCharsets.UTF_8)) {
                if (line.startsWith("rtpMin=")) {
                    rtpMin = Integer.parseInt(line.substring(7));
                } else if (line.startsWith("rtpMax=")) {
                    rtpMax = Integer.parseInt(line.substring(7));
                } else if (line.startsWith("nextId=")) {
                    nextId = Integer.parseInt(line.substring(7));
                } else if (line.startsWith("rtpCommand=")) {
                    // Compatibility with old NPCZ versions. The external RTP command
                    // is intentionally no longer used.
                    continue;
                } else if (line.startsWith("npc=")) {
                    Map<String, String> npc = new LinkedHashMap<>();

                    for (String pair : line.substring(4).split(";")) {
                        if (pair.isEmpty()) continue;

                        String[] parts = pair.split("\\|", 2);
                        if (parts.length == 2) {
                            npc.put(unescape(parts[0]), unescape(parts[1]));
                        }
                    }

                    if (npc.containsKey("id")) {
                        npcs.put(Integer.parseInt(npc.get("id")), npc);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String escape(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String unescape(String value) {
        try {
            return new String(
                    Base64.getDecoder().decode(value),
                    StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            return value;
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}

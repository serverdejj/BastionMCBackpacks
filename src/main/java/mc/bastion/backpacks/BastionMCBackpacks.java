package mc.bastion.backpacks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class BastionMCBackpacks extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final int MAX = 25;
    private static final List<Integer> LEGACY_MENU_SLOTS = List.of(10,11,12,13,14,19,20,21,22,23,28,29,30,31,32,37,38,39,40,41,46,47,48,49,50);
    private static final List<Integer> PREVIOUS_MENU_SLOTS = List.of(2,3,4,5,6,11,12,13,14,15,20,21,22,23,24,29,30,31,32,33,39,40,41,42,43);
    private static final List<Integer> LAST_MENU_SLOTS = List.of(2,3,4,5,6,11,12,13,14,15,20,21,22,23,24,29,30,31,32,33,38,39,40,41,42);
    private static final List<Integer> NEW_MENU_SLOTS = List.of(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40);
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private FileConfiguration messages;
    private File dataDir;
    private final Map<UUID, Map<Integer, Inventory>> loaded = new HashMap<>();
    private BukkitTask autosave;

    // Separate inventory holders allow reliable identification without inspecting titles.
    private static final class BackpackHolder implements InventoryHolder {
        final UUID player;
        final int number;
        Inventory inventory;
        BackpackHolder(UUID player, int number) { this.player = player; this.number = number; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class MenuHolder implements InventoryHolder {
        final UUID viewer;
        final UUID target;
        final boolean administrative;
        Inventory inventory;
        MenuHolder(UUID viewer, UUID target, boolean administrative) { this.viewer = viewer; this.target = target; this.administrative = administrative; }
        @Override public Inventory getInventory() { return inventory; }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        migrateMenuLayout();
        if (!new File(getDataFolder(), "messages.yml").exists()) saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        dataDir = new File(getDataFolder(), "data");
        if (!dataDir.exists() && !dataDir.mkdirs()) getLogger().warning("No se pudo crear carpeta data.");
        var command = getCommand("pv");
        if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
        getServer().getPluginManager().registerEvents(this, this);
        int seconds = Math.max(10, getConfig().getInt("data.autosave-seconds", 60));
        autosave = Bukkit.getScheduler().runTaskTimer(this, this::saveAll, seconds * 20L, seconds * 20L);
        getLogger().info("25 mochilas habilitadas. Default: mochilas 1, 2 y 3.");
    }

    @Override public void onDisable() {
        if (autosave != null) autosave.cancel();
        saveAll();
        loaded.clear();
    }

    // Update only the exact old default layout; keep hand-customized slots intact.
    private void migrateMenuLayout() {
        boolean changed = false;
        if (getConfig().getIntegerList("menu.slots").equals(LEGACY_MENU_SLOTS)
                || getConfig().getIntegerList("menu.slots").equals(PREVIOUS_MENU_SLOTS)
                || getConfig().getIntegerList("menu.slots").equals(LAST_MENU_SLOTS)) {
            getConfig().set("menu.slots", NEW_MENU_SLOTS);
            changed = true;
        }
        if ("GRAY_STAINED_GLASS_PANE".equalsIgnoreCase(getConfig().getString("menu.filler.material", ""))) {
            getConfig().set("menu.filler.material", "BLACK_STAINED_GLASS_PANE");
            changed = true;
        }
        if (changed) {
            saveConfig();
            getLogger().info("Menu de mochilas actualizado: bordes de cristal negro y ultima fila reubicada.");
        }
    }

    private String color(String value) {
        if (value == null) return "";
        Matcher matcher = HEX.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char digit : hex.toCharArray()) replacement.append('§').append(digit);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    private String render(String value, int number, int slots, int unlocked) {
        return color(value.replace("{number}", Integer.toString(number))
                .replace("{slots}", Integer.toString(slots))
                .replace("{unlocked}", Integer.toString(unlocked)));
    }

    private void tell(CommandSender sender, String key) {
        sender.sendMessage(color(messages.getString("prefix", "") + messages.getString(key, key)));
    }

    private void tell(CommandSender sender, String key, String target, int number) {
        String raw = messages.getString("prefix", "") + messages.getString(key, key);
        sender.sendMessage(color(raw.replace("{player}", target).replace("{number}", Integer.toString(number))));
    }

    private String adminPermission(String path, String fallback) {
        return getConfig().getString(path, fallback);
    }

    private OfflinePlayer findKnownPlayer(String username) {
        Player online = Bukkit.getPlayerExact(username);
        if (online != null) return online;
        for (OfflinePlayer candidate : Bukkit.getOfflinePlayers()) {
            if (candidate.getName() != null && candidate.getName().equalsIgnoreCase(username)) return candidate;
        }
        return null; // Avoid generating a new empty profile for a name that never joined.
    }

    private String playerName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private void reloadSettings() {
        reloadConfig();
        migrateMenuLayout();
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        // Keep already opened inventories alive: never clear the cache during reload.
    }

    private Material material(String key, String fallback) {
        Material found = Material.matchMaterial(getConfig().getString(key, fallback));
        return found != null && found.isItem() ? found : Material.BARREL;
    }

    private int size(int number) {
        int size = getConfig().getInt("backpack.sizes." + number, getConfig().getInt("backpack.default-size", 27));
        return size >= 9 && size <= 54 && size % 9 == 0 ? size : 27;
    }

    private String node(int number) {
        return getConfig().getString("permissions.nodes." + number, "bastionbackpacks.use." + number);
    }

    private boolean permitted(Player p, int number) {
        return p.hasPermission(getConfig().getString("permissions.admin-bypass", "bastionbackpacks.admin"))
                || p.hasPermission(node(number));
    }

    private int unlocked(Player p) {
        int total = 0;
        for (int i = 1; i <= MAX; ++i) if (permitted(p, i)) total++;
        return total;
    }

    private ItemStack menuItem(boolean unlocked, int number) {
        String prefix = unlocked ? "menu.unlocked" : "menu.locked";
        ItemStack item = new ItemStack(material(prefix + ".material", "BARREL"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(render(getConfig().getString(prefix + ".name", "Mochila #{number}"), number, size(number), 0));
            List<String> lore = getConfig().getStringList(prefix + ".lore");
            meta.setLore(lore.stream().map(x -> render(x, number, size(number), 0)).collect(Collectors.toList()));
            meta.setEnchantmentGlintOverride(getConfig().getBoolean(prefix + ".glow", unlocked));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void showMenu(Player p) {
        showMenu(p, p.getUniqueId(), p.getName(), false);
    }

    private void showMenu(Player p, UUID target, String targetName, boolean administrative) {
        int guiSize = getConfig().getInt("menu.size", 54);
        if (guiSize < 27 || guiSize > 54 || guiSize % 9 != 0) guiSize = 54;
        MenuHolder holder = new MenuHolder(p.getUniqueId(), target, administrative);
        holder.inventory = Bukkit.createInventory(holder, guiSize,
                render((administrative ? getConfig().getString("admin-menu.title", "Mochilas de {player}") : getConfig().getString("menu.title", "Mochilas"))
                    .replace("{player}", targetName), 0, 0, unlocked(p)));
        Inventory menu = holder.inventory;
        if (getConfig().getBoolean("menu.filler.enabled", true)) {
            ItemStack filler = new ItemStack(material("menu.filler.material", "BLACK_STAINED_GLASS_PANE"));
            ItemMeta meta = filler.getItemMeta();
            if (meta != null) { meta.setDisplayName(color(getConfig().getString("menu.filler.name", " "))); filler.setItemMeta(meta); }
            for (int i = 0; i < guiSize; i++) menu.setItem(i, filler);
        }
        List<Integer> slots = getConfig().getIntegerList("menu.slots");
        if (slots.size() < MAX) slots = NEW_MENU_SLOTS;
        for (int i = 1; i <= MAX; i++) {
            int slot = slots.get(i - 1);
            if (slot >= 0 && slot < guiSize) menu.setItem(slot, menuItem(administrative || permitted(p, i), i));
        }
        p.openInventory(menu);
    }

    private int numberForSlot(int slot) {
        List<Integer> slots = getConfig().getIntegerList("menu.slots");
        if (slots.size() < MAX) slots = NEW_MENU_SLOTS;
        for (int i = 0; i < MAX; ++i) if (slots.get(i) == slot) return i + 1;
        return -1;
    }

    private File file(UUID id, int number) {
        File folder = new File(dataDir, id.toString());
        return new File(folder, number + ".yml");
    }

    private Inventory load(UUID target, int number) {
        Map<Integer, Inventory> byNumber = loaded.computeIfAbsent(target, x -> new HashMap<>());
        Inventory previous = byNumber.get(number);
        if (previous != null) return previous;
        File file = file(target, number);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int desiredSize = size(number);
        int savedSize = yaml.getInt("size", desiredSize);
        // Prevent truncating saved inventory when configured capacity is decreased.
        if (getConfig().getBoolean("data.protect-items-from-size-reduction", true)
                && savedSize > desiredSize && savedSize <= 54 && savedSize % 9 == 0) desiredSize = savedSize;
        BackpackHolder holder = new BackpackHolder(target, number);
        holder.inventory = Bukkit.createInventory(holder, desiredSize,
                render(getConfig().getString("backpack.title", "Mochila #{number}"), number, desiredSize, 0));
        List<?> items = yaml.getList("items", Collections.emptyList());
        for (int i = 0; i < Math.min(desiredSize, items.size()); i++) {
            Object obj = items.get(i);
            if (obj instanceof ItemStack stack) holder.inventory.setItem(i, stack);
        }
        byNumber.put(number, holder.inventory);
        return holder.inventory;
    }

    private boolean store(UUID player, int number, Inventory inventory) {
        File target = file(player, number);
        try {
            Path directory = target.toPath().getParent();
            Files.createDirectories(directory);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("size", inventory.getSize());
            // Arrays.asList preserves all slot positions including empty ones.
            yaml.set("items", Arrays.asList(inventory.getContents()));
            Path temp = Files.createTempFile(directory, "backpack-", ".tmp");
            try {
                yaml.save(temp.toFile());
                try { Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temp); }
            return true;
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Error guardando mochila " + number + " del jugador " + player, ex);
            return false;
        }
    }

    private void savePlayer(UUID id) {
        Map<Integer, Inventory> entries = loaded.get(id);
        if (entries != null) entries.forEach((number, inv) -> store(id, number, inv));
    }

    private void saveAll() {
        loaded.forEach((id, entries) -> entries.forEach((number, inv) -> store(id, number, inv)));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(adminPermission("permissions.reload", "bastionbackpacks.reload"))) {
                tell(sender, "no-permission"); return true;
            }
            // Save current data first; reload changes configuration but never discards inventories.
            saveAll();
            reloadSettings();
            tell(sender, "reloaded");
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("ver") || args[0].equalsIgnoreCase("see"))) {
            if (!sender.hasPermission(adminPermission("permissions.admin-view", "bastionbackpacks.admin.view"))) {
                tell(sender, "no-permission"); return true;
            }
            if (!(sender instanceof Player p)) { tell(sender, "only-players"); return true; }
            if (args.length < 2 || args.length > 3) { tell(sender, "admin-usage"); return true; }
            OfflinePlayer target = findKnownPlayer(args[1]);
            if (target == null) { tell(sender, "unknown-player"); return true; }
            String targetName = playerName(target);
            if (args.length == 2) {
                showMenu(p, target.getUniqueId(), targetName, true);
                return true;
            }
            int number;
            try { number = Integer.parseInt(args[2]); }
            catch (NumberFormatException ex) { tell(sender, "invalid-number"); return true; }
            if (number < 1 || number > MAX) { tell(sender, "invalid-number"); return true; }
            p.openInventory(load(target.getUniqueId(), number));
            tell(sender, "admin-opened", targetName, number);
            return true;
        }
        if (!(sender instanceof Player p)) { tell(sender, "only-players"); return true; }
        if (args.length == 0) { showMenu(p); return true; }
        if (args.length != 1) { tell(p, "invalid-number"); return true; }
        int number;
        try { number = Integer.parseInt(args[0]); }
        catch (NumberFormatException ex) { tell(p, "invalid-number"); return true; }
        if (number < 1 || number > MAX) { tell(p, "invalid-number"); return true; }
        if (!permitted(p, number)) { tell(p, "locked"); return true; }
        p.openInventory(load(p.getUniqueId(), number));
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender instanceof Player p) {
                for (int n = 1; n <= MAX; ++n) if (permitted(p, n)) options.add(Integer.toString(n));
            }
            if (sender.hasPermission(adminPermission("permissions.reload", "bastionbackpacks.reload"))) options.add("reload");
            if (sender.hasPermission(adminPermission("permissions.admin-view", "bastionbackpacks.admin.view"))) options.add("ver");
            return options.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("ver") || args[0].equalsIgnoreCase("see"))
                && sender.hasPermission(adminPermission("permissions.admin-view", "bastionbackpacks.admin.view"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(x -> x.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("ver") || args[0].equalsIgnoreCase("see"))
                && sender.hasPermission(adminPermission("permissions.admin-view", "bastionbackpacks.admin.view"))) {
            return java.util.stream.IntStream.rangeClosed(1, MAX).mapToObj(Integer::toString)
                    .filter(x -> x.startsWith(args[2])).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof MenuHolder holder && holder.viewer.equals(p.getUniqueId())) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) return;
            int number = numberForSlot(event.getRawSlot());
            if (number < 1) return;
            if (!holder.administrative && !permitted(p, number)) { tell(p, "locked"); return; }
            p.openInventory(load(holder.target, number));
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BackpackHolder holder) {
            if (!store(holder.player, holder.number, event.getInventory())) {
                if (event.getPlayer() instanceof Player p) tell(p, "data-error");
            }
            Bukkit.getScheduler().runTask(this, () -> evictOfflineIfUnused(holder.player));
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        savePlayer(uuid);
        // Do not evict an inventory another administrator is currently editing.
        Bukkit.getScheduler().runTask(this, () -> evictOfflineIfUnused(uuid));
    }

    private void evictOfflineIfUnused(UUID uuid) {
        Map<Integer, Inventory> entries = loaded.get(uuid);
        if (entries == null || Bukkit.getPlayer(uuid) != null) return;
        for (Inventory inventory : entries.values()) if (!inventory.getViewers().isEmpty()) return;
        savePlayer(uuid);
        loaded.remove(uuid);
    }
}

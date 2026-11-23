package mc.bastion.backpacks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
        final UUID player;
        Inventory inventory;
        MenuHolder(UUID player) { this.player = player; }
        @Override public Inventory getInventory() { return inventory; }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
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

    private void tell(Player player, String key) {
        player.sendMessage(color(messages.getString("prefix", "") + messages.getString(key, key)));
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
        int guiSize = getConfig().getInt("menu.size", 54);
        if (guiSize < 27 || guiSize > 54 || guiSize % 9 != 0) guiSize = 54;
        MenuHolder holder = new MenuHolder(p.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, guiSize,
                render(getConfig().getString("menu.title", "Mochilas"), 0, 0, unlocked(p)));
        Inventory menu = holder.inventory;
        if (getConfig().getBoolean("menu.filler.enabled", true)) {
            ItemStack filler = new ItemStack(material("menu.filler.material", "GRAY_STAINED_GLASS_PANE"));
            ItemMeta meta = filler.getItemMeta();
            if (meta != null) { meta.setDisplayName(color(getConfig().getString("menu.filler.name", " "))); filler.setItemMeta(meta); }
            for (int i = 0; i < guiSize; i++) menu.setItem(i, filler);
        }
        List<Integer> slots = getConfig().getIntegerList("menu.slots");
        if (slots.size() < MAX) slots = Arrays.asList(10,11,12,13,14,19,20,21,22,23,28,29,30,31,32,37,38,39,40,41,46,47,48,49,50);
        for (int i = 1; i <= MAX; i++) {
            int slot = slots.get(i - 1);
            if (slot >= 0 && slot < guiSize) menu.setItem(slot, menuItem(permitted(p, i), i));
        }
        p.openInventory(menu);
    }

    private int numberForSlot(int slot) {
        List<Integer> slots = getConfig().getIntegerList("menu.slots");
        if (slots.size() < MAX) slots = Arrays.asList(10,11,12,13,14,19,20,21,22,23,28,29,30,31,32,37,38,39,40,41,46,47,48,49,50);
        for (int i = 0; i < MAX; ++i) if (slots.get(i) == slot) return i + 1;
        return -1;
    }

    private File file(UUID id, int number) {
        File folder = new File(dataDir, id.toString());
        return new File(folder, number + ".yml");
    }

    private Inventory load(Player player, int number) {
        Map<Integer, Inventory> byNumber = loaded.computeIfAbsent(player.getUniqueId(), x -> new HashMap<>());
        Inventory previous = byNumber.get(number);
        if (previous != null) return previous;
        File file = file(player.getUniqueId(), number);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int desiredSize = size(number);
        int savedSize = yaml.getInt("size", desiredSize);
        // Prevent truncating saved inventory when configured capacity is decreased.
        if (getConfig().getBoolean("data.protect-items-from-size-reduction", true)
                && savedSize > desiredSize && savedSize <= 54 && savedSize % 9 == 0) desiredSize = savedSize;
        BackpackHolder holder = new BackpackHolder(player.getUniqueId(), number);
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
        if (!(sender instanceof Player p)) {
            sender.sendMessage(color(messages.getString("prefix", "") + messages.getString("only-players", "Solo jugadores")));
            return true;
        }
        if (args.length == 0) { showMenu(p); return true; }
        if (args.length != 1) { tell(p, "invalid-number"); return true; }
        int number;
        try { number = Integer.parseInt(args[0]); }
        catch (NumberFormatException ex) { tell(p, "invalid-number"); return true; }
        if (number < 1 || number > MAX) { tell(p, "invalid-number"); return true; }
        if (!permitted(p, number)) { tell(p, "locked"); return true; }
        p.openInventory(load(p, number));
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player p)) return Collections.emptyList();
        return java.util.stream.IntStream.rangeClosed(1, MAX)
                .filter(n -> permitted(p, n))
                .mapToObj(Integer::toString)
                .filter(s -> s.startsWith(args[0]))
                .collect(Collectors.toList());
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof MenuHolder holder && holder.player.equals(p.getUniqueId())) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) return;
            int number = numberForSlot(event.getRawSlot());
            if (number < 1) return;
            if (!permitted(p, number)) { tell(p, "locked"); return; }
            p.openInventory(load(p, number));
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
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        savePlayer(uuid);
        loaded.remove(uuid);
    }
}

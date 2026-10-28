package com.wolfool.bestiary;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 보스 관리 — 몹 목록을 읽고, 능력치와 드랍을 고치고, 스폰 알을 뽑는다.
 *
 * 고친 값은 MythicMobs 파일에 바로 적힌다 (주석은 Yml 이 줄 단위로 다뤄서 그대로 남는다).
 * 게임에 반영하려면 /mm reload — 창의 단추로도 된다.
 */
public final class BestiaryPlugin extends JavaPlugin implements Listener {

    private MobIndex index;
    private Path backupDir;
    private NamespacedKey eggKey;
    private final Map<UUID, Consumer<String>> waiting = new HashMap<>();

    @Override
    public void onEnable() {
        backupDir = getDataFolder().toPath().resolve("_backup");
        eggKey = new NamespacedKey(this, "mob");
        index = scan();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("활성화 완료 — 몹 " + index.mobs().size() + "종 · 드랍표 "
                + index.tables().size() + "개");
        selfCheck();
    }

    /** 드랍표가 실제로 붙는지 몇 마리 찍어 본다 — 로그만 봐도 알 수 있게. */
    private void selfCheck() {
        Map<Path, Yml> cache = new HashMap<>();
        int withDrops = 0;
        int viaTable = 0;
        List<String> sample = new ArrayList<>();
        for (MobIndex.Mob m : index.mobs()) {
            try {
                MobIndex.DropRef ref = index.resolve(m, cache);
                List<String> lines = ref.yml().list(ref.block(), "Drops");
                if (lines.isEmpty()) {
                    continue;
                }
                withDrops++;
                if (ref.table()) {
                    viaTable++;
                }
                if (sample.size() < 3) {
                    sample.add(m.id() + " -> " + ref.label() + " · " + lines.size() + "줄");
                }
            } catch (Exception ignored) {
                // 못 읽는 몹은 점검에서 뺀다
            }
        }
        getLogger().info("드랍 점검 — 드랍 있는 몹 " + withDrops + "마리 · 그중 드랍표 참조 "
                + viaTable + "마리");
        for (String s : sample) {
            getLogger().info("  " + s);
        }
    }

    private MobIndex scan() {
        Path plugins = getDataFolder().toPath().getParent();
        Path mythic = plugins.resolve("MythicMobs");
        if (!Files.isDirectory(mythic)) {
            getLogger().warning("MythicMobs 폴더를 못 찾았습니다: " + mythic);
        }
        return MobIndex.scan(mythic);
    }

    public void reindex() {
        index = scan();
        Ce.clear();
    }

    public MobIndex index() {
        return index;
    }

    public Path backupDir() {
        return backupDir;
    }

    // ─────────────────────────────────────────────────────────── 명령

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("게임 안에서 써 주십시오.");
            return true;
        }
        if (!p.hasPermission("bestiary.admin")) {
            p.sendMessage(Msg.of("&c이 창은 관리자만 열 수 있습니다 &8(bestiary.admin)"));
            return true;
        }
        if (args.length > 0 && (args[0].equals("리로드") || args[0].equalsIgnoreCase("reload"))) {
            mmReload(p);
            return true;
        }
        new ListGui(this, p, args.length > 0 ? String.join(" ", args) : "").open();
        return true;
    }

    /** 탭 자동완성 — '리로드' + 몹 이름/아이디 (앞글자 맞는 것만, 40개까지). */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length != 1 || index == null) {
            return List.of();
        }
        String q = args[0].toLowerCase();
        List<String> out = new ArrayList<>();
        if ("리로드".startsWith(q) || "reload".startsWith(q)) {
            out.add("리로드");
        }
        // 보스를 먼저 보여 준다 — 이름이 기억나지 않아도 골라 쓸 수 있게
        for (boolean wantBoss : new boolean[]{true, false}) {
            for (MobIndex.Mob m : index.mobs()) {
                if (out.size() >= 60) {
                    return out;
                }
                if (m.boss() != wantBoss) {
                    continue;
                }
                String name = m.plain();
                if (name.toLowerCase().startsWith(q) || m.id().toLowerCase().startsWith(q)) {
                    if (!out.contains(m.id())) {
                        out.add(m.id());
                    }
                }
            }
        }
        return out;
    }

    public void mmReload(CommandSender to) {
        reindex();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm reload");
        if (to != null) {
            to.sendMessage(Msg.of("&aMythicMobs 에게 다시 읽으라고 했습니다 &8(10초쯤 걸립니다)"));
        }
    }

    // ─────────────────────────────────────────────────────────── 채팅 입력

    /** 값을 손으로 치게 한다. 예: "4-7" 또는 "35%" */
    public void ask(Player p, String prompt, Consumer<String> then) {
        waiting.put(p.getUniqueId(), then);
        p.closeInventory();
        p.sendMessage(Msg.of(prompt));
        p.sendMessage(Msg.of("&8취소하려면 '취소' 라고 치세요"));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        String text = e.getMessage();
        if (!take(e.getPlayer(), text)) {
            return;
        }
        e.setCancelled(true);
    }

    /**
     * 요즘 Paper 는 AsyncChatEvent 쪽을 먼저 쏠 수 있다. 둘 다 받아 두고
     * 먼저 온 쪽이 가져가게 한다 (waiting 에서 지워지므로 두 번 처리되지 않는다).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChatModern(io.papermc.paper.event.player.AsyncChatEvent e) {
        String text = flatten(e.message());
        if (!take(e.getPlayer(), text)) {
            return;
        }
        // 아무도 못 보게 한다 — 보는 사람이 없으면 아무에게도 안 뜬다
        e.viewers().clear();
        try {
            e.setCancelled(true);
        } catch (Throwable ignored) {
            // 취소를 막는 Paper 판도 있어서 실패해도 그냥 넘어간다
        }
    }

    /** 채팅 한 줄을 입력값으로 가져간다. 가져갔으면 true. */
    private boolean take(Player p, String raw) {
        Consumer<String> then = waiting.remove(p.getUniqueId());
        if (then == null) {
            return false;
        }
        String text = raw == null ? "" : raw.trim();
        if (text.equalsIgnoreCase("취소") || text.equalsIgnoreCase("cancel")) {
            p.sendMessage(Msg.of("&7취소했습니다."));
            return true;
        }
        // 채팅 스레드에서 파일을 만지면 안 되므로 메인 스레드로 넘긴다
        Bukkit.getScheduler().runTask(this, () -> then.accept(text));
        return true;
    }

    // ─────────────────────────────────────────────────────────── 창 클릭

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Gui gui) {
            gui.click(e);
        }
    }

    // ─────────────────────────────────────────────────────────── 스폰 알

    public ItemStack egg(MobIndex.Mob mob) {
        ItemStack it = new ItemStack(eggMaterial(mob.type()));
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Msg.of("&f" + MobIndex.strip(mob.display()) + " &7스폰 알"));
        List<String> lore = new ArrayList<>();
        lore.add(Msg.of("&7놓으면 그 자리에 나옵니다"));
        lore.add(Msg.of(""));
        lore.add(Msg.of("&8종류: " + mob.type()));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(eggKey, PersistentDataType.STRING, mob.id());
        it.setItemMeta(meta);
        return it;
    }

    private static Material eggMaterial(String type) {
        String t = type == null ? "" : type.trim();
        int colon = t.indexOf(':');
        if (colon >= 0) {
            t = t.substring(colon + 1);
        }
        try {
            return Material.valueOf(t.toUpperCase() + "_SPAWN_EGG");
        } catch (IllegalArgumentException e) {
            return Material.SPAWNER;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta()) {
            return;
        }
        String id = it.getItemMeta().getPersistentDataContainer().get(eggKey, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        if (!e.getPlayer().hasPermission("bestiary.admin")) {
            return;
        }
        e.setCancelled(true);
        var b = e.getClickedBlock();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format(
                "mm mobs spawn %s 1 %s,%d,%d,%d",
                id, b.getWorld().getName(), b.getX(), b.getY() + 1, b.getZ()));
        if (e.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
            it.setAmount(it.getAmount() - 1);
        }
    }

    // ─────────────────────────────────────────────────────────── 공용

    /** 색 같은 꾸밈을 떼고 글자만 모은다 (직렬화기가 없어서 직접 훑는다). */
    private static String flatten(net.kyori.adventure.text.Component c) {
        StringBuilder b = new StringBuilder();
        if (c instanceof net.kyori.adventure.text.TextComponent tc) {
            b.append(tc.content());
        }
        for (net.kyori.adventure.text.Component child : c.children()) {
            b.append(flatten(child));
        }
        return b.toString();
    }

    static ItemStack plain(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Msg.of(name));
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    static void save(Yml yml, Path backupDir, Player p) {
        try {
            yml.save(backupDir);
        } catch (IOException e) {
            p.sendMessage(Msg.of("&c저장하지 못했습니다: " + e.getMessage()));
            return;
        }
        p.sendMessage(Msg.of("&a저장했습니다 &8— &7게임에 반영하려면 &e/mm reload"));
    }
}

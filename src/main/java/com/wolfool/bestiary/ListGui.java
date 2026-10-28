package com.wolfool.bestiary;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 몹 목록.
 *
 * 이 창의 목적은 하나다 — **무엇을 떨구는지 한눈에 보이는 것**. 그래서 아이콘
 * 설명에 드랍 이름·확률·개수를 그대로 적는다.
 */
public final class ListGui implements Gui {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_SEARCH = 48;
    private static final int SLOT_FILTER = 49;
    private static final int SLOT_HELP = 50;
    private static final int SLOT_NEXT = 53;

    private final BestiaryPlugin plugin;
    private final Player viewer;
    private final Inventory inventory;
    private String query;
    private boolean bossOnly;
    private int page;

    public ListGui(BestiaryPlugin plugin, Player viewer, String query) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.query = query == null ? "" : query;
        this.inventory = Bukkit.createInventory(this, SIZE, Msg.of("&8보스 관리"));
        render();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void open() {
        viewer.openInventory(inventory);
    }

    private List<MobIndex.Mob> shown() {
        List<MobIndex.Mob> out = new ArrayList<>();
        String q = MobIndex.strip(query).toLowerCase();
        for (MobIndex.Mob m : plugin.index().mobs()) {
            if (bossOnly && !m.boss()) {
                continue;
            }
            if (!q.isEmpty()) {
                String hay = (m.plain() + " " + m.id() + " " + m.type()).toLowerCase();
                if (!hay.contains(q)) {
                    continue;
                }
            }
            out.add(m);
        }
        return out;
    }

    private void render() {
        inventory.clear();
        List<MobIndex.Mob> list = shown();
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }
        // 파일을 한 번만 읽도록 이 화면을 그리는 동안만 캐시한다
        Map<Path, Yml> cache = new HashMap<>();
        int from = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < list.size(); i++) {
            inventory.setItem(i, icon(list.get(from + i), cache));
        }

        inventory.setItem(SLOT_SEARCH, BestiaryPlugin.plain(Material.COMPASS,
                query.isEmpty() ? "&7검색" : "&e검색: &f" + query,
                List.of(Msg.of("&7쓰는 법: &f/보스 <낱말>"),
                        Msg.of("&7이름 · 아이디 · 종류에서 찾습니다"),
                        query.isEmpty()
                                ? Msg.of("&e클릭 &7채팅창에 검색어를 채웁니다 (&fTAB&7 자동완성)")
                                : Msg.of("&e클릭 &7검색 지우기"))));

        inventory.setItem(SLOT_FILTER, BestiaryPlugin.plain(
                bossOnly ? Material.WITHER_SKELETON_SKULL : Material.SKELETON_SKULL,
                bossOnly ? "&e보스만 보는 중" : "&e전부 보는 중",
                List.of(Msg.of("&7지금 " + list.size() + "종"),
                        Msg.of("&e클릭 &7" + (bossOnly ? "전부 보기" : "보스만 보기")),
                        Msg.of("&8보스 = 체력 100 이상이거나 BossBar 가 있는 것"))));

        inventory.setItem(SLOT_HELP, BestiaryPlugin.plain(Material.BOOK, "&f쓰는 법",
                List.of(Msg.of("&e좌클릭 &7능력치 고치기"),
                        Msg.of("&e우클릭 &7스폰 알 받기"),
                        Msg.of("&e쉬프트+좌클릭 &7드랍 바로 열기"),
                        Msg.of(""),
                        Msg.of("&e나침반 &7검색 · &e해골 &7보스만 보기"),
                        Msg.of(""),
                        Msg.of("&7드랍 창에서는 &f가운데클릭&7으로"),
                        Msg.of("&7확률을 직접 치고, &f쉬프트+클릭&7으로"),
                        Msg.of("&7아래 가방의 아이템을 더합니다"),
                        Msg.of(""),
                        Msg.of("&7고친 것은 파일에 바로 적히고"),
                        Msg.of("&7게임 반영은 &f/mm reload"))));

        inventory.setItem(SLOT_PREV, BestiaryPlugin.plain(Material.ARROW,
                "&f이전 쪽", List.of(Msg.of("&7" + (page + 1) + " / " + pages))));
        inventory.setItem(SLOT_NEXT, BestiaryPlugin.plain(Material.ARROW,
                "&f다음 쪽", List.of(Msg.of("&7" + (page + 1) + " / " + pages))));
    }

    private ItemStack icon(MobIndex.Mob mob, Map<Path, Yml> cache) {
        List<String> lore = new ArrayList<>();
        lore.add(Msg.of("&7아이디 &f" + mob.id()));
        lore.add(Msg.of("&7종류 &f" + mob.type() + "   &c체력 &f" + trim(mob.health())
                + "   &6피해 &f" + trim(mob.damage())));
        lore.add(Msg.of("&8" + shortFile(mob.file())));
        lore.add(Msg.of(""));

        List<Yml.Drop> drops = new ArrayList<>();
        String label = null;
        try {
            MobIndex.DropRef ref = plugin.index().resolve(mob, cache);
            drops.addAll(parse(ref.yml().list(ref.block(), "Drops")));
            label = ref.label();
            if (ref.table()) {
                // 표 말고 몹 파일에 직접 적어 둔 것도 같이 보여 준다
                for (String line : cache.get(mob.file()).list(mob.id(), "Drops")) {
                    String body = line.trim();
                    if (body.startsWith("- ")) {
                        body = body.substring(2).trim();
                    }
                    if (!body.contains(" ") && plugin.index().tables().containsKey(body)) {
                        continue;
                    }
                    Yml.Drop d = Yml.Drop.parse(line);
                    if (d != null) {
                        drops.add(d);
                    }
                }
            }
        } catch (Exception ex) {
            lore.add(Msg.of("&c드랍을 못 읽었습니다"));
        }

        lore.add(Msg.of("&e드랍 &8(" + (label == null ? "?" : label) + ")"));
        if (drops.isEmpty()) {
            lore.add(Msg.of("&8 아무것도 안 떨굽니다"));
        }
        int shown = 0;
        for (Yml.Drop d : drops) {
            if (shown >= 7) {
                lore.add(Msg.of("&8 … 외 " + (drops.size() - shown) + "종"));
                break;
            }
            lore.add(Msg.of("&f " + shortItem(d.item()) + " &8×" + d.amount()
                    + " &7· &e" + d.chanceText()));
            shown++;
        }
        lore.add(Msg.of(""));
        lore.add(Msg.of("&e좌클릭 &7고치기"));
        lore.add(Msg.of("&e우클릭 &7스폰 알 받기"));

        String name = "&f" + mob.plain() + (mob.boss() ? " &c[보스]" : "");
        return BestiaryPlugin.plain(iconMaterial(mob), name, lore);
    }

    /** 스폰 알이 있으면 그걸, 없으면 주민 알로 대신한다. */
    private static Material iconMaterial(MobIndex.Mob mob) {
        return switch (mob.type().toUpperCase()) {
            case "ZOMBIE" -> Material.ZOMBIE_SPAWN_EGG;
            case "SKELETON" -> Material.SKELETON_SPAWN_EGG;
            case "BLAZE" -> Material.BLAZE_SPAWN_EGG;
            case "SPIDER" -> Material.SPIDER_SPAWN_EGG;
            case "WITHER_SKELETON" -> Material.WITHER_SKELETON_SPAWN_EGG;
            case "ENDERMAN" -> Material.ENDERMAN_SPAWN_EGG;
            case "SLIME" -> Material.SLIME_SPAWN_EGG;
            default -> Material.valueOf(safeEgg(mob.type()));
        };
    }

    private static String safeEgg(String type) {
        String t = type == null ? "" : type.toUpperCase();
        int colon = t.indexOf(':');
        if (colon >= 0) {
            t = t.substring(colon + 1);
        }
        String candidate = t + "_SPAWN_EGG";
        try {
            Material.valueOf(candidate);
            return candidate;
        } catch (IllegalArgumentException e) {
            return "VILLAGER_SPAWN_EGG";
        }
    }

    private static List<Yml.Drop> parse(List<String> lines) {
        List<Yml.Drop> out = new ArrayList<>();
        for (String l : lines) {
            Yml.Drop d = Yml.Drop.parse(l);
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    /** 'craftengine:buglab:chitin' -> 'buglab:chitin' */
    private static String shortItem(String item) {
        return item.startsWith("craftengine:") ? item.substring("craftengine:".length()) : item;
    }

    private static String shortFile(Path p) {
        Path parent = p.getParent();
        String dir = parent == null ? "" : parent.getFileName().toString();
        return dir + "/" + p.getFileName();
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : Yml.Drop.trim(v);
    }

    @Override
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || p != viewer) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot >= SIZE || slot < 0) {
            return;     // 아래 가방은 건드리지 않는다
        }
        e.setCancelled(true);

        if (slot == SLOT_PREV) {
            page--;
            render();
            return;
        }
        if (slot == SLOT_NEXT) {
            page++;
            render();
            return;
        }
        if (slot == SLOT_FILTER) {
            bossOnly = !bossOnly;
            page = 0;
            render();
            return;
        }
        if (slot == SLOT_SEARCH) {
            // 채팅창에 '/보스 ' 를 채워 준다 — 그 뒤로 TAB 자동완성이 듣는다.
            // (마인크래프트에 채팅창을 코드로 채우는 길은 없고, 눌러서 채우는
            //  길만 있다. 그래서 한 번 더 눌러야 한다.)
            p.closeInventory();
            String cmd = query.isEmpty() ? "/보스 " : "/보스 " + query;
            p.sendMessage(Msg.of("&b[보스] &f검색어를 치려면 아래를 누르세요:"));
            p.sendMessage(net.kyori.adventure.text.Component.text("[ " + cmd + " ]")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(cmd))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            net.kyori.adventure.text.Component.text("누르면 채팅창에 채워집니다"))));
            p.sendMessage(Msg.of("&8TAB 을 누르면 보스 이름이 자동완성됩니다"));
            return;
        }

        List<MobIndex.Mob> list = shown();
        int i = page * PER_PAGE + slot;
        if (slot >= PER_PAGE || i >= list.size()) {
            return;
        }
        MobIndex.Mob mob = list.get(i);
        if (e.isShiftClick() && e.isLeftClick()) {
            new DropGui(plugin, viewer, mob).open();
            return;
        }
        if (e.isRightClick()) {
            p.getInventory().addItem(plugin.egg(mob)).forEach((n, left) ->
                    p.getWorld().dropItemNaturally(p.getLocation(), left));
            p.sendMessage(Msg.of("&a" + mob.plain() + " &7스폰 알을 받았습니다"));
            return;
        }
        new StatGui(plugin, viewer, mob).open();
    }
}

package com.wolfool.bestiary;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 드랍 고치기.
 *
 * 한 줄에 아이템·개수(몇 개에서 몇 개)·확률이 다 들어 있다. 뭉툭하게 오르내리는
 * 것만으로는 0.35 같은 값을 맞추기 어려워서, 가운데클릭으로 숫자를 직접 친다.
 */
public final class DropGui implements Gui {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_HELP = 46;
    private static final int SLOT_RELOAD = 47;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_MM = 53;

    private final BestiaryPlugin plugin;
    private final Player viewer;
    private final MobIndex.Mob mob;
    private final Inventory inventory;

    private Yml yml;
    private String block;
    private String label = "?";
    private boolean table;
    private int page;

    public DropGui(BestiaryPlugin plugin, Player viewer, MobIndex.Mob mob) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.mob = mob;
        this.inventory = Bukkit.createInventory(this, SIZE, Msg.of("&8드랍 — " + MobIndex.strip(mob.display())));
        load();
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

    private void load() {
        try {
            MobIndex.DropRef ref = plugin.index().resolve(mob);
            yml = ref.yml();
            block = ref.block();
            label = ref.label();
            table = ref.table();
        } catch (IOException e) {
            yml = null;
            viewer.sendMessage(Msg.of("&c드랍을 못 읽었습니다: " + e.getMessage()));
        }
    }

    private List<Yml.Drop> drops() {
        List<Yml.Drop> out = new ArrayList<>();
        if (yml == null) {
            return out;
        }
        for (String line : yml.list(block, "Drops")) {
            Yml.Drop d = Yml.Drop.parse(line);
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    private void render() {
        inventory.clear();
        List<Yml.Drop> list = drops();
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }
        int from = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < list.size(); i++) {
            inventory.setItem(i, icon(list.get(from + i)));
        }

        List<String> help = new ArrayList<>();
        help.add(Msg.of("&7한 줄에 아이템 · 개수 · 확률이 다 있습니다"));
        help.add(Msg.of(""));
        help.add(Msg.of("&e좌클릭 &7확률 +5%"));
        help.add(Msg.of("&e우클릭 &7확률 -5%"));
        help.add(Msg.of("&e쉬프트+좌/우 &7확률 ±25%"));
        help.add(Msg.of("&e가운데클릭 &7숫자를 직접 치기"));
        help.add(Msg.of("&eQ &7그 드롭을 지웁니다"));
        help.add(Msg.of(""));
        help.add(Msg.of("&7더하기 — 아래 가방에서 아이템을"));
        help.add(Msg.of("&7&e쉬프트+클릭 &7하면 드랍으로 더해집니다"));
        inventory.setItem(SLOT_HELP, BestiaryPlugin.plain(Material.BOOK, "&f쓰는 법", help));

        inventory.setItem(SLOT_BACK, BestiaryPlugin.plain(Material.ARROW, "&f돌아가기",
                List.of(Msg.of("&7" + MobIndex.strip(mob.display()) + " 로"))));
        inventory.setItem(SLOT_RELOAD, BestiaryPlugin.plain(Material.REDSTONE, "&a다시 읽기",
                List.of(Msg.of("&7파일에서 다시 읽어 옵니다"))));
        inventory.setItem(SLOT_MM, BestiaryPlugin.plain(Material.REDSTONE_BLOCK, "&a/mm reload",
                List.of(Msg.of("&7게임에 반영합니다"))));
        inventory.setItem(SLOT_PAGE, BestiaryPlugin.plain(Material.PAPER,
                "&f" + (page + 1) + " / " + pages,
                List.of(Msg.of("&7" + (table ? "드랍표 " + label : "몹 파일에 직접 적힌 드랍")),
                        Msg.of("&7드랍 총 &f" + list.size() + "줄"))));
    }

    private ItemStack icon(Yml.Drop d) {
        ItemStack base = Ce.iconForDrop(d.item());
        ItemStack it = base.clone();
        var meta = it.getItemMeta();
        meta.setDisplayName(Msg.of("&f" + shortItem(d.item())));
        List<String> lore = new ArrayList<>();
        lore.add(Msg.of("&7확률 &e" + d.chanceText() + "   &7개수 &f" + d.amount() + "개"));
        if (!d.tail().isEmpty()) {
            lore.add(Msg.of("&8뒤에 붙은 것: " + d.tail()));
        }
        lore.add(Msg.of(""));
        lore.add(Msg.of("&e좌클릭 &7확률 +5%   &e우클릭 &7-5%"));
        lore.add(Msg.of("&e쉬프트+좌/우 &7확률 ±25%"));
        lore.add(Msg.of("&e가운데클릭 &7직접 입력"));
        lore.add(Msg.of("&eQ &7지우기"));
        lore.add(Msg.of(""));
        lore.add(Msg.of("&8파일에 적히는 줄:"));
        lore.add(Msg.of("&8- " + d.toLine()));
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static String shortItem(String item) {
        return item.startsWith("craftengine:") ? item.substring("craftengine:".length()) : item;
    }

    @Override
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || p != viewer) {
            return;
        }
        if (e.getClickedInventory() == null) {
            return;
        }
        if (!e.getClickedInventory().equals(inventory)) {
            // 아래 가방 — 시프트+클릭이면 드랍으로 더한다
            if (e.getClick().isShiftClick() && e.getCurrentItem() != null
                    && !e.getCurrentItem().getType().isAir()) {
                e.setCancelled(true);
                add(e.getCurrentItem());
            }
            return;
        }

        e.setCancelled(true);
        int slot = e.getRawSlot();

        if (slot == SLOT_BACK) {
            new StatGui(plugin, viewer, mob).open();
            return;
        }
        if (slot == SLOT_RELOAD) {
            load();
            render();
            return;
        }
        if (slot == SLOT_MM) {
            plugin.mmReload(p);
            return;
        }
        if (slot >= PER_PAGE || slot < 0) {
            return;
        }

        List<Yml.Drop> list = drops();
        int i = page * PER_PAGE + slot;
        if (i >= list.size()) {
            return;
        }
        Yml.Drop d = list.get(i);

        ClickType type = e.getClick();
        if (type == ClickType.DROP || type == ClickType.CONTROL_DROP) {
            list.remove(i);
            write(list);
            render();
            p.sendMessage(Msg.of("&7드롭을 지웠습니다: &f" + shortItem(d.item())));
            return;
        }
        // 가운데클릭은 생존에선 MIDDLE, 크리에이티브에선 CREATIVE 로 온다
        if (type == ClickType.MIDDLE || type == ClickType.CREATIVE) {
            plugin.ask(p, "&e확률과 개수를 치세요 &7— 예: &f35% 4-7 &7(하나만 쳐도 됩니다)",
                    text -> applyText(i, text));
            return;
        }

        Yml.Drop changed;
        if (type.isShiftClick()) {
            double delta = type.isRightClick() ? -0.25 : 0.25;
            changed = withChance(d, d.chance() + delta);
        } else if (type.isRightClick()) {
            changed = withChance(d, d.chance() - 0.05);
        } else if (type.isLeftClick()) {
            changed = withChance(d, d.chance() + 0.05);
        } else {
            return;
        }
        list.set(i, changed);
        write(list);
        render();
    }

    private void applyText(int index, String text) {
        List<Yml.Drop> list = drops();
        if (index >= list.size()) {
            return;
        }
        Yml.Drop d = list.get(index);
        Double chance = null;
        int min = Integer.MIN_VALUE;
        int max = Integer.MIN_VALUE;

        for (String token : text.trim().split("\\s+")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.contains("-") && t.matches("\\d+-\\d+")) {
                String[] ab = t.split("-");
                min = Integer.parseInt(ab[0]);
                max = Math.max(min, Integer.parseInt(ab[1]));
            } else if (t.endsWith("%")) {
                Double v = num(t.substring(0, t.length() - 1));
                if (v != null) {
                    chance = v / 100.0;
                }
            } else if (t.matches("\\d+")) {
                if (chance == null) {
                    Double v = num(t);
                    // 100 이하면 확률로, 넘으면 개수로 본다
                    if (v != null && v <= 100) {
                        chance = v > 1.0 ? v / 100.0 : v;
                    } else {
                        min = max = Integer.parseInt(t);
                    }
                } else {
                    min = max = Integer.parseInt(t);
                }
            } else {
                Double v = num(t);
                if (v != null && chance == null) {
                    chance = v;
                }
            }
        }
        if (chance != null) {
            d = withChance(d, chance);
        }
        if (min != Integer.MIN_VALUE) {
            d = new Yml.Drop(d.item(), min, max, d.chance(), d.tail(), d.table());
        }
        list.set(index, d);
        write(list);
        render();
    }

    private static Double num(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Yml.Drop withChance(Yml.Drop d, double chance) {
        double c = Math.max(0, Math.min(1, chance));
        return new Yml.Drop(d.item(), d.min(), d.max(), Math.round(c * 10000.0) / 10000.0, d.tail(), d.table());
    }

    private void write(List<Yml.Drop> list) {
        if (yml == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (Yml.Drop d : list) {
            lines.add(d.toLine());
        }
        yml.setList(block, "Drops", lines);
        BestiaryPlugin.save(yml, plugin.backupDir(), viewer);
    }

    /** 아래 가방의 아이템을 드랍으로 더한다. */
    private void add(ItemStack stack) {
        String id = Ce.idOf(stack);
        String item = id != null ? "craftengine:" + id : stack.getType().name();
        List<Yml.Drop> list = drops();
        list.add(new Yml.Drop(item, 1, 1, 1.0, "", null));
        write(list);
        render();
        viewer.sendMessage(Msg.of("&a드랍으로 더했습니다: &f" + shortItem(item)
                + " &8— 개수와 확률을 클릭으로 고치세요"));
    }
}

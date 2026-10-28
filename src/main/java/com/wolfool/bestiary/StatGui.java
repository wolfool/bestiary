package com.wolfool.bestiary;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 몹 한 마리의 능력치. 좌/우클릭으로 오르내리고, 가운데클릭으로 직접 친다. */
public final class StatGui implements Gui {

    private static final int SIZE = 27;
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15};
    private static final int SLOT_DROPS = 16;
    private static final int SLOT_EGG = 21;
    private static final int SLOT_RELOAD = 22;
    private static final int SLOT_BACK = 26;

    private record Field(String key, String name, double step, String help) {
    }

    private static final List<Field> FIELDS = List.of(
            new Field("Health", "&c체력", 1, "몹이 버티는 양"),
            new Field("Damage", "&6평타 피해", 1, "붙어서 때릴 때의 값. 0 이면 스킬만 아픕니다"),
            new Field("Armor", "&f방어", 1, "높을수록 덜 아픕니다"),
            new Field("Options.MovementSpeed", "&b이동 속도", 0.05, "좀비가 0.23 입니다. 0.5 면 달리는 사람보다 빠릅니다"),
            new Field("Options.KnockbackResistance", "&7밀림 저항", 0.1, "1 이면 아예 안 밀립니다"),
            new Field("Options.FollowRange", "&a쫓는 거리", 1, "이 안에 들어오면 쫓아옵니다 (블록)"));

    private final BestiaryPlugin plugin;
    private final Player viewer;
    private final MobIndex.Mob mob;
    private final Inventory inventory;
    private Yml yml;

    public StatGui(BestiaryPlugin plugin, Player viewer, MobIndex.Mob mob) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.mob = mob;
        this.inventory = Bukkit.createInventory(this, SIZE,
                Msg.of("&8" + MobIndex.strip(mob.display()) + " &7고치기"));
        try {
            this.yml = Yml.read(mob.file());
        } catch (IOException e) {
            viewer.sendMessage(Msg.of("&c파일을 못 읽었습니다: " + e.getMessage()));
        }
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

    private void render() {
        inventory.clear();
        for (int i = 0; i < FIELDS.size(); i++) {
            Field f = FIELDS.get(i);
            String raw = yml == null ? null : yml.get(mob.id(), f.key());
            List<String> lore = new ArrayList<>();
            lore.add(Msg.of("&7지금 &e" + (raw == null ? "(안 적혀 있음)" : raw)));
            lore.add(Msg.of(f.help()));
            lore.add(Msg.of(""));
            lore.add(Msg.of("&e좌클릭 &7+" + Yml.Drop.trim(f.step())
                    + "   &e우클릭 &7-" + Yml.Drop.trim(f.step())));
            lore.add(Msg.of("&e쉬프트 &7다섯 배"));
            lore.add(Msg.of("&e가운데클릭 &7직접 입력"));
            if (raw == null) {
                lore.add(Msg.of("&8없는 값이라 클릭하면 새로 만듭니다"));
            }
            inventory.setItem(SLOTS[i], BestiaryPlugin.plain(
                    Material.PAPER, "&f" + f.name(), lore));
        }

        int dropCount = 0;
        String label = "?";
        try {
            MobIndex.DropRef ref = plugin.index().resolve(mob);
            label = ref.label();
            dropCount = ref.yml().list(ref.block(), "Drops").size();
        } catch (IOException ignored) {
            // 못 읽으면 0 으로 둔다
        }
        inventory.setItem(SLOT_DROPS, BestiaryPlugin.plain(Material.CHEST, "&6드랍",
                List.of(Msg.of("&7무엇을 떨구는지 고칩니다"),
                        Msg.of("&7지금 &f" + dropCount + "줄 &8(" + label + ")"),
                        Msg.of(""),
                        Msg.of("&e클릭 &7열기"))));

        inventory.setItem(SLOT_EGG, plugin.egg(mob));

        inventory.setItem(SLOT_RELOAD, BestiaryPlugin.plain(Material.REDSTONE, "&a다시 읽기",
                List.of(Msg.of("&7MythicMobs 에게 파일을 다시 읽으라고 합니다"),
                        Msg.of("&8고친 것은 이미 파일에 저장돼 있습니다"),
                        Msg.of("&8이 단추는 그것을 게임에 반영합니다"),
                        Msg.of(""),
                        Msg.of("&e클릭 &7/mm reload"))));

        inventory.setItem(SLOT_BACK, BestiaryPlugin.plain(Material.ARROW, "&f목록으로",
                List.of(Msg.of("&7아이디 &f" + mob.id()),
                        Msg.of("&8" + mob.file().getFileName()))));
    }

    @Override
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || p != viewer) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot >= SIZE || slot < 0) {
            return;
        }
        e.setCancelled(true);

        if (slot == SLOT_BACK) {
            new ListGui(plugin, viewer, "").open();
            return;
        }
        if (slot == SLOT_DROPS) {
            new DropGui(plugin, viewer, mob).open();
            return;
        }
        if (slot == SLOT_RELOAD) {
            plugin.mmReload(p);
            return;
        }
        if (slot == SLOT_EGG) {
            p.getInventory().addItem(plugin.egg(mob)).forEach((n, left) ->
                    p.getWorld().dropItemNaturally(p.getLocation(), left));
            return;
        }

        int idx = -1;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                idx = i;
            }
        }
        if (idx < 0) {
            return;
        }
        Field f = FIELDS.get(idx);

        // 가운데클릭은 생존에선 MIDDLE, 크리에이티브에선 CREATIVE 로 온다
        if (e.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE
                || e.getClick() == org.bukkit.event.inventory.ClickType.CREATIVE) {
            plugin.ask(p, "&e" + MobIndex.strip(f.name()) + " &7값을 치세요 (예: " + example(f) + ")",
                    text -> applyText(f, text));
            return;
        }

        double step = f.step() * (e.isShiftClick() ? 5 : 1);
        double cur = parse(yml == null ? null : yml.get(mob.id(), f.key()), 0);
        double next = cur + (e.isRightClick() ? -step : step);
        if (next < 0) {
            next = 0;
        }
        write(f, Yml.Drop.trim(round(next, f.step())));
        render();
    }

    private void applyText(Field f, String text) {
        String t = text.trim();
        if (t.startsWith("+") || t.startsWith("-")) {
            double delta = parse(t, Double.NaN);
            if (!Double.isNaN(delta)) {
                double cur = parse(yml == null ? null : yml.get(mob.id(), f.key()), 0);
                write(f, Yml.Drop.trim(round(Math.max(0, cur + delta), f.step())));
                new StatGui(plugin, viewer, mob).open();
            }
            return;
        }
        double v = parse(t, Double.NaN);
        if (Double.isNaN(v)) {
            viewer.sendMessage(Msg.of("&c숫자가 아닙니다: " + t));
        } else {
            write(f, Yml.Drop.trim(v));
        }
        new StatGui(plugin, viewer, mob).open();
    }

    private void write(Field f, String value) {
        if (yml == null) {
            return;
        }
        if (yml.set(mob.id(), f.key(), value)) {
            BestiaryPlugin.save(yml, plugin.backupDir(), viewer);
        }
    }

    private static String example(Field f) {
        return f.step() < 1 ? "0.35" : "120";
    }

    private static double round(double v, double step) {
        double r = Math.round(v / step) * step;
        return Math.round(r * 10000.0) / 10000.0;
    }

    private static double parse(String s, double fallback) {
        if (s == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

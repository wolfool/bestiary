package com.wolfool.bestiary;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * CraftEngine 아이템 아이콘.
 *
 * 드랍의 대부분이 'craftengine:buglab:chitin' 같은 것이라, 종이로 보이면
 * 목록이 읽히지 않는다. CraftEngine 에 물어봐서 진짜 아이템을 받아 온다.
 * 리플렉션인 이유: Bestiary 는 CraftEngine 없이도 떠야 한다.
 */
public final class Ce {

    private static Method byId;
    private static boolean tried;
    private static final Map<String, ItemStack> CACHE = new HashMap<>();

    private Ce() {
    }

    /** 못 찾으면 null — 부르는 쪽이 종이로 대신한다. */
    public static ItemStack icon(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        if (CACHE.containsKey(rawId)) {
            return CACHE.get(rawId);
        }
        ItemStack built = build(rawId);
        CACHE.put(rawId, built);
        return built;
    }

    /** 파일을 고쳤을 수 있으니 캐시를 비운다. */
    public static void clear() {
        CACHE.clear();
    }

    /** 아이템 id 를 알려 준다 (CraftEngine 이면 'craftengine:' 를 뗀 값). */
    public static String idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()
                || !stack.getItemMeta().hasItemModel()) {
            return null;
        }
        return stack.getItemMeta().getItemModel().toString();
    }

    /** 드랍 항목 id 로 쓸 이름 — craftengine:x:y 는 'craftengine:x:y' 그대로. */
    public static ItemStack iconForDrop(String drop) {
        String id = drop;
        if (id.startsWith("craftengine:")) {
            id = id.substring("craftengine:".length());
        }
        ItemStack ce = icon(id);
        if (ce != null) {
            return ce;
        }
        Material m = Material.matchMaterial(drop.toUpperCase());
        if (m != null && !m.isAir()) {
            return new ItemStack(m);
        }
        return new ItemStack(Material.PAPER);
    }

    private static ItemStack build(String id) {
        connect();
        if (byId == null) {
            return null;
        }
        try {
            Object def = byId.invoke(null, id);
            if (def == null) {
                return null;
            }
            Object made = def.getClass().getMethod("buildBukkitItem").invoke(def);
            if (made instanceof ItemStack s && !s.getType().isAir()) {
                return s;
            }
        } catch (Throwable ignored) {
            // 못 만들면 종이로 대신한다
        }
        return null;
    }

    private static void connect() {
        if (tried) {
            return;
        }
        tried = true;
        if (Bukkit.getPluginManager().getPlugin("CraftEngine") == null) {
            return;
        }
        try {
            Class<?> c = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            byId = c.getMethod("byId", String.class);
        } catch (Throwable ignored) {
            byId = null;
        }
    }
}

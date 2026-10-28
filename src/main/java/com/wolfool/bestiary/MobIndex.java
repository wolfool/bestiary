package com.wolfool.bestiary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** MythicMobs 폴더를 훑어 몹 목록과 드랍표 목록을 만든다. */
public final class MobIndex {

    /** 몹 한 마리. */
    public record Mob(String id, Path file, String display, String type,
                      double health, double damage, boolean boss) {

        /** 색 코드를 뺀 이름 — 정렬·검색에 쓴다. */
        public String plain() {
            return display == null || display.isEmpty() ? id : strip(display);
        }
    }

    /** 드랍을 어디서 읽고 쓸지. */
    public record DropRef(Yml yml, String block, boolean table, String label) {
    }

    private final List<Mob> mobs;
    private final Map<String, Path> tables;

    private MobIndex(List<Mob> mobs, Map<String, Path> tables) {
        this.mobs = mobs;
        this.tables = tables;
    }

    public List<Mob> mobs() {
        return mobs;
    }

    public Map<String, Path> tables() {
        return tables;
    }

    public Mob byId(String id) {
        for (Mob m : mobs) {
            if (m.id().equals(id)) {
                return m;
            }
        }
        return null;
    }

    /** 몹이 실제로 떨구는 것 — 표를 쓰면 그 표를, 아니면 몹 파일 안의 Drops 를. */
    public DropRef resolve(Mob mob) throws IOException {
        return resolve(mob, new java.util.HashMap<>());
    }

    /** 같은 화면을 그리는 동안에는 파일을 한 번만 읽는다. */
    public DropRef resolve(Mob mob, Map<Path, Yml> cache) throws IOException {
        Yml yml = cache.computeIfAbsent(mob.file(), p -> {
            try {
                return Yml.read(p);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        for (String line : yml.list(mob.id(), "Drops")) {
            // '  - bug_ant_drops' -> 'bug_ant_drops'
            String body = line.trim();
            if (body.startsWith("- ")) {
                body = body.substring(2).trim();
            }
            if (body.isEmpty() || body.contains(" ")) {
                continue;   // 한 낱말짜리만 표 이름일 수 있다
            }
            Path t = tables.get(body);
            if (t != null) {
                Yml ty = cache.computeIfAbsent(t, p -> {
                    try {
                        return Yml.read(p);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
                return new DropRef(ty, body, true, body);
            }
        }
        return new DropRef(yml, mob.id(), false, mob.id() + " (몹 파일)");
    }

    public static MobIndex scan(Path mythicDir) {
        Map<String, Path> tables = new LinkedHashMap<>();
        for (Path p : ymlFiles(mythicDir.resolve("droptables"))) {
            addTables(tables, p);
        }
        Path packs = mythicDir.resolve("packs");
        if (Files.isDirectory(packs)) {
            try (Stream<Path> s = Files.list(packs)) {
                for (Path pack : s.toList()) {
                    for (Path p : ymlFiles(pack.resolve("droptables"))) {
                        addTables(tables, p);
                    }
                }
            } catch (IOException ignored) {
                // 팩 폴더를 못 읽으면 표 목록이 조금 짧아질 뿐이다
            }
        }

        List<Mob> mobs = new ArrayList<>();
        for (Path p : mobFiles(mythicDir)) {
            collectMobs(p, mobs);
        }
        mobs.sort(Comparator.comparing(Mob::plain, String.CASE_INSENSITIVE_ORDER));
        return new MobIndex(mobs, tables);
    }

    private static List<Path> mobFiles(Path mythicDir) {
        List<Path> out = new ArrayList<>(ymlFiles(mythicDir.resolve("mobs")));
        Path packs = mythicDir.resolve("packs");
        if (Files.isDirectory(packs)) {
            try (Stream<Path> s = Files.list(packs)) {
                for (Path pack : s.toList()) {
                    out.addAll(ymlFiles(pack.resolve("mobs")));
                }
            } catch (IOException ignored) {
                // 위와 같다
            }
        }
        return out;
    }

    private static List<Path> ymlFiles(Path dir) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".yml") || n.endsWith(".yaml");
                    })
                    .sorted()
                    .forEach(out::add);
        } catch (IOException ignored) {
            // 못 읽는 파일은 건너뛴다
        }
        return out;
    }

    private static void addTables(Map<String, Path> out, Path file) {
        try {
            for (String key : Yml.read(file).topKeys()) {
                out.putIfAbsent(key, file);
            }
        } catch (IOException ignored) {
            // 못 읽으면 그냥 넘어간다
        }
    }

    private static void collectMobs(Path file, List<Mob> out) {
        Yml yml;
        try {
            yml = Yml.read(file);
        } catch (IOException e) {
            return;
        }
        for (String id : yml.topKeys()) {
            String type = yml.get(id, "Type");
            if (type == null || type.isBlank()) {
                continue;   // Type 이 없으면 몹 정의가 아니다
            }
            String display = yml.get(id, "Display");
            double health = parseDouble(yml.get(id, "Health"), 1);
            double damage = parseDouble(yml.get(id, "Damage"), 0);
            String bar = yml.get(id, "BossBar");
            if (bar == null) {
                bar = yml.get(id, "Options.BossBar");
            }
            boolean boss = bar != null || health >= 100;
            out.add(new Mob(id, file, display, type, health, damage, boss));
        }
    }

    private static double parseDouble(String s, double fallback) {
        if (s == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 색 코드 제거. */
    public static String strip(String s) {
        if (s == null) {
            return "";
        }
        String out = s.replaceAll("(?i)&[0-9a-fk-or]", "")
                .replaceAll("&#[0-9a-fA-F]{6}", "")
                .replaceAll("<[^>]+>", "");
        return out.trim();
    }
}

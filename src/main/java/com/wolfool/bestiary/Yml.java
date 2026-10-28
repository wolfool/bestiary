package com.wolfool.bestiary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 줄 단위 YAML 손보개.
 *
 * MythicMobs 파일은 주석이 빽빽하다. SnakeYAML 로 읽었다 쓰면 그 주석이 전부
 * 날아가므로, 줄을 그대로 들고 있다가 고칠 줄만 바꾼다.
 */
public final class Yml {

    private final Path path;
    private final List<String> lines;

    private Yml(Path path, List<String> lines) {
        this.path = path;
        this.lines = lines;
    }

    public static Yml read(Path path) throws IOException {
        return new Yml(path, new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8)));
    }

    public Path path() {
        return path;
    }

    /** 최상위 키 (들여쓰기 0). */
    public List<String> topKeys() {
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            if (l.isEmpty() || l.charAt(0) == ' ' || l.charAt(0) == '#') {
                continue;
            }
            int c = l.indexOf(':');
            if (c > 0) {
                out.add(l.substring(0, c).trim());
            }
        }
        return out;
    }

    private int blockStart(String key) {
        String want = key + ":";
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(want)) {
                return i;
            }
        }
        return -1;
    }

    /** 블록의 끝(다음 최상위 키 직전). */
    private int blockEnd(int start) {
        for (int i = start + 1; i < lines.size(); i++) {
            String l = lines.get(i);
            if (!l.isEmpty() && l.charAt(0) != ' ' && l.charAt(0) != '#') {
                return i;
            }
        }
        return lines.size();
    }

    /** 'Options.FollowRange' 같은 점 표기를 따라가 그 줄 번호를 찾는다. */
    private int findKey(int from, int to, String dotted) {
        String[] parts = dotted.split("\\.");
        int lo = from;
        int hi = to;
        for (int p = 0; p < parts.length; p++) {
            String indent = "  ".repeat(p + 1);
            String want = indent + parts[p] + ":";
            int found = -1;
            for (int i = lo; i < hi; i++) {
                if (lines.get(i).startsWith(want)) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                return -1;
            }
            if (p == parts.length - 1) {
                return found;
            }
            // 다음 단계는 이 키의 아래쪽만 본다
            lo = found + 1;
            hi = found + 1;
            while (hi < to && (lines.get(hi).startsWith(indent + "  ") || lines.get(hi).trim().isEmpty())) {
                hi++;
            }
        }
        return -1;
    }

    public String get(String block, String dotted) {
        int s = blockStart(block);
        if (s < 0) {
            return null;
        }
        int k = findKey(s + 1, blockEnd(s), dotted);
        if (k < 0) {
            return null;
        }
        String l = lines.get(k);
        String v = l.substring(l.indexOf(':') + 1);
        int hash = v.indexOf(" #");
        if (hash >= 0) {
            v = v.substring(0, hash);
        }
        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    /** 값을 바꾼다. 줄이 없으면 블록 끝에 만든다. true = 바뀜. */
    public boolean set(String block, String dotted, String value) {
        int s = blockStart(block);
        if (s < 0) {
            return false;
        }
        int e = blockEnd(s);
        int k = findKey(s + 1, e, dotted);
        String[] parts = dotted.split("\\.");
        if (k >= 0) {
            String l = lines.get(k);
            String head = l.substring(0, l.indexOf(':') + 1);
            String old = l.substring(l.indexOf(':') + 1);
            int hash = old.indexOf(" #");
            String comment = hash >= 0 ? old.substring(hash) : "";
            lines.set(k, head + " " + value + comment);
            return true;
        }
        // 없으면 만든다 — 중간 단계(Options 등)도 필요하면 만든다
        int at = s + 1;
        for (int p = 0; p < parts.length - 1; p++) {
            String indent = "  ".repeat(p + 1);
            String want = indent + parts[p] + ":";
            int found = -1;
            for (int i = at; i < e; i++) {
                if (lines.get(i).startsWith(want)) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                lines.add(at, want);
                found = at;
                e++;
            }
            at = found + 1;
            while (at < e && (lines.get(at).startsWith(indent + "  ") || lines.get(at).trim().isEmpty())) {
                at++;
            }
            e = at;
        }
        lines.add(at, "  ".repeat(parts.length) + parts[parts.length - 1] + ": " + value);
        return true;
    }

    /** 블록 안에서 '이름:' 섹션의 줄 범위. 없으면 start=end=-1. */
    private int[] section(String block, String name) {
        int s = blockStart(block);
        if (s < 0) {
            return new int[]{-1, -1};
        }
        int e = blockEnd(s);
        int k = findKey(s + 1, e, name);
        if (k < 0) {
            return new int[]{-1, -1};
        }
        // 목록 섹션은 '  - ' 줄과 빈 줄만 이어진다. '  Skills:' 같은 형제 키가
        // 나오면 거기서 멈춘다 — 안 그러면 드랍을 저장할 때 스킬 줄까지 지운다.
        int end = k + 1;
        while (end < e) {
            String l = lines.get(end);
            if (l.startsWith("  - ") || l.trim().isEmpty()) {
                end++;
                continue;
            }
            break;
        }
        return new int[]{k, end};
    }

    /** '  Drops:' 아래의 항목 줄들. */
    public List<String> list(String block, String name) {
        int[] sec = section(block, name);
        List<String> out = new ArrayList<>();
        if (sec[0] < 0) {
            return out;
        }
        for (int i = sec[0] + 1; i < sec[1]; i++) {
            String l = lines.get(i);
            if (l.startsWith("  - ")) {
                out.add(l);
            }
        }
        return out;
    }

    /** 목록 전체를 갈아 끼운다. 섹션이 없으면 만든다. */
    public void setList(String block, String name, List<String> items) {
        int[] sec = section(block, name);
        if (sec[0] < 0) {
            int s = blockStart(block);
            if (s < 0) {
                return;
            }
            int at = s + 1;
            List<String> add = new ArrayList<>();
            add.add("  " + name + ":");
            for (String it : items) {
                add.add("  - " + it);
            }
            lines.addAll(at, add);
            return;
        }
        for (int i = sec[1] - 1; i > sec[0]; i--) {
            if (lines.get(i).startsWith("  - ")) {
                lines.remove(i);
            }
        }
        List<String> add = new ArrayList<>();
        for (String it : items) {
            add.add("  - " + it);
        }
        lines.addAll(sec[0] + 1, add);
    }

    /** 물리기 전에 원본을 _backup 에 남기고 저장. */
    public void save(Path backupDir) throws IOException {
        if (Files.exists(path)) {
            Files.createDirectories(backupDir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path dst = backupDir.resolve(path.getFileName().toString() + "." + stamp);
            if (!Files.exists(dst)) {
                Files.copy(path, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    // ────────────────────────────────────────────────────────── 드랍 한 줄

    /** MythicMobs 드랍 한 줄. '표 이름' 한 줄짜리 참조도 같은 그릇에 담는다. */
    public record Drop(String item, int min, int max, double chance, String tail, String table) {

        public static Drop parse(String line) {
            // 줄은 '  - 내용' 으로 온다 — 앞의 두 칸을 먼저 떼야 '- ' 가 보인다
            String s = line.trim();
            if (s.startsWith("- ")) {
                s = s.substring(2).trim();
            } else if (s.equals("-")) {
                return null;
            }
            String[] p = s.split("\\s+");
            if (p.length == 0 || p[0].isEmpty()) {
                return null;
            }
            if (p.length == 1) {
                return new Drop(p[0], 1, 1, 1.0, "", null);
            }
            int min;
            int max;
            if (p[1].matches("\\d+")) {
                min = max = Integer.parseInt(p[1]);
            } else if (p[1].matches("\\d+-\\d+")) {
                String[] ab = p[1].split("-");
                min = Integer.parseInt(ab[0]);
                max = Integer.parseInt(ab[1]);
                if (max < min) {
                    max = min;
                }
            } else {
                // 모르는 형태는 건드리지 않도록 그대로 들고 있는다
                return new Drop(p[0], 1, 1, 1.0, String.join(" ", Arrays.copyOfRange(p, 1, p.length)), null);
            }
            double chance = 1.0;
            int next = 2;
            if (p.length > 2) {
                String c = p[2];
                Double v = null;
                if (c.endsWith("%")) {
                    v = num(c.substring(0, c.length() - 1));
                    if (v != null) {
                        v = v / 100.0;
                    }
                } else {
                    v = num(c);
                }
                if (v != null) {
                    chance = v;
                    next = 3;
                }
            }
            String tail = next < p.length
                    ? String.join(" ", Arrays.copyOfRange(p, next, p.length)) : "";
            return new Drop(p[0], min, max, chance, tail, null);
        }

        private static Double num(String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public String amount() {
            return min == max ? String.valueOf(min) : min + "-" + max;
        }

        public String chanceText() {
            return trim(chance * 100) + "%";
        }

        public String toLine() {
            StringBuilder b = new StringBuilder(item).append(' ').append(amount());
            if (chance < 1.0 || !tail.isEmpty()) {
                b.append(' ').append(trim(chance));
            }
            if (!tail.isEmpty()) {
                b.append(' ').append(tail);
            }
            return b.toString();
        }

        /** 0.35 -> "0.35", 1.0 -> "1", 35.0 -> "35" */
        public static String trim(double v) {
            String s = String.format("%.4f", v);
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
            return s.isEmpty() ? "0" : s;
        }
    }
}

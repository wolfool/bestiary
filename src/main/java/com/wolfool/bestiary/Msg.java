package com.wolfool.bestiary;

import org.bukkit.ChatColor;

/** & 색 코드를 게임 색으로 바꾼다. */
public final class Msg {

    private Msg() {
    }

    public static String of(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}

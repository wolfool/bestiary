package com.wolfool.bestiary;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/** 이 플러그인이 여는 창. */
public interface Gui extends InventoryHolder {

    void open();

    /** 이 창에서 일어난 클릭. */
    void click(InventoryClickEvent e);
}

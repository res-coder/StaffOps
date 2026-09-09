package com.staffops.gui;
import org.bukkit.Bukkit; import org.bukkit.event.inventory.InventoryClickEvent; import org.bukkit.inventory.*; import net.kyori.adventure.text.Component;
import java.util.*; import java.util.function.Consumer;
public final class StaffMenu implements InventoryHolder {
    private final Inventory inventory; private final Map<Integer,Consumer<InventoryClickEvent>> actions=new HashMap<>();
    public StaffMenu(int rows,Component title){inventory=Bukkit.createInventory(this,rows*9,title);} @Override public Inventory getInventory(){return inventory;}
    public void set(int slot,ItemStack item,Consumer<InventoryClickEvent> action){inventory.setItem(slot,item);if(action!=null)actions.put(slot,action);} public void click(InventoryClickEvent e){Consumer<InventoryClickEvent>a=actions.get(e.getRawSlot());if(a!=null)a.accept(e);}
}

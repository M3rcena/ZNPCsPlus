package lol.pyr.znpcsplus.util;

import lol.pyr.znpcsplus.api.NpcApiProvider;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Listener for Items Adder events to ensure data is loaded before using the API
 */
public class ItemsAdderListener implements Listener {
    private final Plugin plugin;

    public ItemsAdderListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemsAdderLoadData(dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent event) {
        ItemsAdderIntegration.setLoaded(true);
        Bukkit.getLogger().info("[ZNPCsPlus] Items Adder data loaded! NPCs can now use Items Adder items.");

        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                lol.pyr.znpcsplus.api.NpcApi api = NpcApiProvider.get();
                if (api != null && api.getNpcRegistry() instanceof lol.pyr.znpcsplus.npc.NpcRegistryImpl) {
                    lol.pyr.znpcsplus.npc.NpcRegistryImpl registry = (lol.pyr.znpcsplus.npc.NpcRegistryImpl) api
                            .getNpcRegistry();
                    Bukkit.getLogger().info("[ZNPCsPlus] Reloading NPCs to apply Items Adder items...");
                    registry.reload();
                }
            } catch (Exception e) {
                Bukkit.getLogger()
                        .warning("[ZNPCsPlus] Could not reload NPCs after Items Adder loaded: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}

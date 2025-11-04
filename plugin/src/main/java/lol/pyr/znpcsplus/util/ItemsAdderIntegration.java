package lol.pyr.znpcsplus.util;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.ItemsAdder;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for integrating with Items Adder API
 */
public class ItemsAdderIntegration {
    private static Boolean itemsAdderAvailable = null;
    private static boolean itemsAdderLoaded = false;
    private static Set<String> cachedNamespaces = new HashSet<>();
    private static Map<String, List<String>> cachedItemsByNamespace = new HashMap<>();
    private static List<String> cachedAllItemIds = new ArrayList<>();
    private static List<String> pendingDeserializations = new ArrayList<>();
    private static Runnable retryDeserializationCallback = null;

    /**
     * Checks if Items Adder plugin is available
     * 
     * @return true if Items Adder is available, false otherwise
     */
    public static boolean isAvailable() {
        if (itemsAdderAvailable == null) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("ItemsAdder");
            itemsAdderAvailable = plugin != null && plugin.isEnabled();
        }
        return itemsAdderAvailable;
    }

    /**
     * Checks if Items Adder data has been loaded
     * 
     * @return true if Items Adder is loaded, false otherwise
     */
    public static boolean isLoaded() {
        return isAvailable() && itemsAdderLoaded;
    }

    /**
     * Marks Items Adder as loaded (should be called from ItemsAdderLoadDataEvent
     * listener)
     */
    public static void setLoaded(boolean loaded) {
        itemsAdderLoaded = loaded;
        if (loaded) {
            cacheItemsAndNamespaces();
            if (retryDeserializationCallback != null) {
                retryDeserializationCallback.run();
            }
        } else {
            cachedNamespaces.clear();
            cachedItemsByNamespace.clear();
            cachedAllItemIds.clear();
        }
    }

    /**
     * Sets a callback to retry deserialization when Items Adder loads
     */
    public static void setRetryDeserializationCallback(Runnable callback) {
        retryDeserializationCallback = callback;
    }

    /**
     * Adds an item ID to the pending deserializations list
     */
    public static void addPendingDeserialization(String itemId) {
        if (!pendingDeserializations.contains(itemId)) {
            pendingDeserializations.add(itemId);
        }
    }

    /**
     * Caches all available Items Adder items and namespaces for auto-suggestions
     */
    private static void cacheItemsAndNamespaces() {
        if (!isLoaded()) {
            return;
        }

        try {
            cachedNamespaces.clear();
            cachedItemsByNamespace.clear();
            cachedAllItemIds.clear();

            try {
                Set<String> allRegisteredIds = CustomStack.getNamespacedIdsInRegistry();

                if (allRegisteredIds != null && !allRegisteredIds.isEmpty()) {
                    Map<String, List<String>> itemsByNamespace = new HashMap<>();

                    for (String namespacedId : allRegisteredIds) {
                        if (namespacedId != null && namespacedId.contains(":")) {
                            String[] parts = namespacedId.split(":", 2);
                            if (parts.length == 2) {
                                String namespace = parts[0];
                                cachedNamespaces.add(namespace);
                                itemsByNamespace.computeIfAbsent(namespace, k -> new ArrayList<>()).add(namespacedId);
                                if (!cachedAllItemIds.contains(namespacedId)) {
                                    cachedAllItemIds.add(namespacedId);
                                }
                            }
                        }
                    }

                    cachedItemsByNamespace.putAll(itemsByNamespace);
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning(
                        "[ZNPCsPlus] Could not get all registered items from registry, trying fallback method: "
                                + e.getMessage());
                e.printStackTrace();

                String[] commonNamespaces = { "iasurvival", "itemsadder", "custom", "myitems" };
                for (String namespace : commonNamespaces) {
                    try {
                        Collection<CustomStack> items = ItemsAdder.getAllItems(namespace);
                        if (items != null && !items.isEmpty()) {
                            cacheNamespaceItems(namespace, items);
                        }
                    } catch (Exception ex) {
                        // Namespace might not exist
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[ZNPCsPlus] Exception while caching Items Adder items: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Caches items for a specific namespace
     */
    private static void cacheNamespaceItems(String namespace, Collection<CustomStack> items) {
        cachedNamespaces.add(namespace);
        List<String> itemIds = new ArrayList<>();
        for (CustomStack stack : items) {
            if (stack != null && stack.getNamespacedID() != null) {
                String itemId = stack.getNamespacedID();
                if (!cachedAllItemIds.contains(itemId)) {
                    itemIds.add(itemId);
                    cachedAllItemIds.add(itemId);
                }
            }
        }
        cachedItemsByNamespace.put(namespace, itemIds);
    }

    /**
     * Gets all cached Items Adder item IDs
     * 
     * @return List of all cached item IDs
     */
    public static List<String> getAllItemIds() {
        return new ArrayList<>(cachedAllItemIds);
    }

    /**
     * Gets all cached namespaces
     * 
     * @return Set of all cached namespaces
     */
    public static Set<String> getNamespaces() {
        return new HashSet<>(cachedNamespaces);
    }

    /**
     * Gets all item IDs for a specific namespace
     * 
     * @param namespace The namespace to get items for
     * @return List of item IDs for that namespace
     */
    public static List<String> getItemIdsForNamespace(String namespace) {
        return cachedItemsByNamespace.getOrDefault(namespace, Collections.emptyList());
    }

    /**
     * Filters item IDs that match a given prefix (for auto-suggestions)
     * 
     * @param prefix The prefix to filter by
     * @return List of matching item IDs
     */
    public static List<String> getItemIdsMatching(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return getAllItemIds();
        }

        String lowerPrefix = prefix.toLowerCase();
        return cachedAllItemIds.stream()
                .filter(id -> id.toLowerCase().startsWith(lowerPrefix))
                .limit(50) // Limit to 50 suggestions to avoid lag
                .collect(Collectors.toList());
    }

    /**
     * Attempts to get an ItemStack from an Items Adder item ID
     * 
     * @param itemId The Items Adder item ID (e.g., "namespace:item_id")
     * @return ItemStack if the item exists, null otherwise
     */
    public static ItemStack getItemsAdderItem(String itemId) {
        if (!isLoaded()) {
            return null;
        }

        try {
            String[] variationsToTry = { itemId };

            if (itemId.startsWith("itemsadder:")) {
                String withoutPrefix = itemId.substring("itemsadder:".length());
                String[] commonNamespaces = { "iasurvival", "itemsadder", "custom", "myitems" };
                variationsToTry = new String[commonNamespaces.length + 2];
                variationsToTry[0] = itemId;
                variationsToTry[1] = withoutPrefix;
                int idx = 2;
                for (String namespace : commonNamespaces) {
                    variationsToTry[idx++] = namespace + ":" + withoutPrefix;
                }
            } else if (!itemId.contains(":")) {
                String[] commonNamespaces = { "iasurvival", "itemsadder", "custom", "myitems" };
                variationsToTry = new String[commonNamespaces.length + 1];
                variationsToTry[0] = itemId;
                int idx = 1;
                for (String namespace : commonNamespaces) {
                    variationsToTry[idx++] = namespace + ":" + itemId;
                }
            }

            for (String variation : variationsToTry) {
                if (variation == null || variation.isEmpty())
                    continue;

                if (CustomStack.isInRegistry(variation)) {
                    CustomStack customStack = CustomStack.getInstance(variation);
                    if (customStack != null) {
                        ItemStack item = customStack.getItemStack();
                        return item;
                    }
                }
            }

            return null;

        } catch (Exception e) {
            Bukkit.getLogger().warning(
                    "[ZNPCsPlus] Exception while getting Items Adder item '" + itemId + "': " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Checks if a string is a valid Items Adder item ID format
     * Items Adder item IDs are in the format "namespace:item_id"
     * 
     * @param itemId The string to check
     * @return true if it looks like an Items Adder item ID, false otherwise
     */
    public static boolean isItemsAdderItemId(String itemId) {
        return itemId != null && itemId.contains(":") && !itemId.startsWith("minecraft:");
    }

    /**
     * Tries to parse an item string that could be either:
     * - An Items Adder item ID (e.g., "namespace:item_id")
     * - A regular item identifier
     * 
     * @param itemString The item string to parse
     * @return ItemStack if successfully parsed, null otherwise
     */
    public static ItemStack parseItemString(String itemString) {
        if (itemString == null || itemString.isEmpty()) {
            return null;
        }

        if (isItemsAdderItemId(itemString)) {
            return getItemsAdderItem(itemString);
        }

        return null;
    }

    /**
     * Attempts to get the Items Adder item ID from a Bukkit ItemStack
     * 
     * @param itemStack The ItemStack to check
     * @return Items Adder item ID if it's an Items Adder item, null otherwise
     */
    public static String getItemsAdderItemId(ItemStack itemStack) {
        if (!isLoaded() || itemStack == null) {
            return null;
        }

        try {
            CustomStack customStack = CustomStack.byItemStack(itemStack);
            if (customStack != null && customStack.getNamespacedID() != null) {
                return customStack.getNamespacedID();
            }
        } catch (Exception e) {
        }

        return null;
    }
}

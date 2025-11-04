package lol.pyr.znpcsplus.entity.serializers;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import lol.pyr.znpcsplus.entity.PropertySerializer;
import lol.pyr.znpcsplus.util.ItemSerializationUtil;
import lol.pyr.znpcsplus.util.ItemsAdderIntegration;

public class ItemStackPropertySerializer implements PropertySerializer<ItemStack> {
    @Override
    public String serialize(ItemStack property) {
        org.bukkit.inventory.ItemStack bukkitStack = SpigotConversionUtil.toBukkitItemStack(property);

        String itemsAdderId = ItemsAdderIntegration.getItemsAdderItemId(bukkitStack);
        if (itemsAdderId != null) {
            return "itemsadder:" + itemsAdderId;
        }

        return ItemSerializationUtil.itemToB64(bukkitStack);
    }

    @Override
    public ItemStack deserialize(String property) {
        if (property == null) {
            return null;
        }

        if (property.startsWith("itemsadder:")) {
            String itemsAdderId = property.substring("itemsadder:".length());

            if (!ItemsAdderIntegration.isLoaded()) {
                ItemsAdderIntegration.addPendingDeserialization(itemsAdderId);
                return null;
            }

            org.bukkit.inventory.ItemStack bukkitStack = ItemsAdderIntegration.getItemsAdderItem(itemsAdderId);
            if (bukkitStack != null) {
                return SpigotConversionUtil.fromBukkitItemStack(bukkitStack);
            }
            return null;
        }

        return SpigotConversionUtil.fromBukkitItemStack(ItemSerializationUtil.itemFromB64(property));
    }

    @Override
    public Class<ItemStack> getTypeClass() {
        return ItemStack.class;
    }
}

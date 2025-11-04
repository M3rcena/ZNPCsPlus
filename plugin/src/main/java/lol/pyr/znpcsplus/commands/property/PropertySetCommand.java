package lol.pyr.znpcsplus.commands.property;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import lol.pyr.director.adventure.command.CommandContext;
import lol.pyr.director.adventure.command.CommandHandler;
import lol.pyr.director.common.command.CommandExecutionException;
import lol.pyr.znpcsplus.api.entity.EntityProperty;
import lol.pyr.znpcsplus.entity.EntityPropertyImpl;
import lol.pyr.znpcsplus.entity.properties.attributes.AttributeProperty;
import lol.pyr.znpcsplus.npc.NpcEntryImpl;
import lol.pyr.znpcsplus.npc.NpcImpl;
import lol.pyr.znpcsplus.npc.NpcRegistryImpl;
import lol.pyr.znpcsplus.util.*;
import lol.pyr.znpcsplus.util.ItemsAdderIntegration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PropertySetCommand implements CommandHandler {
    private final NpcRegistryImpl npcRegistry;

    public PropertySetCommand(NpcRegistryImpl npcRegistry) {
        this.npcRegistry = npcRegistry;
    }

    @Override
    public void run(CommandContext context) throws CommandExecutionException {
        context.setUsage(context.getLabel() + " property set <id> <property> <value>");
        NpcEntryImpl entry = context.parse(NpcEntryImpl.class);
        NpcImpl npc = entry.getNpc();
        EntityPropertyImpl<?> property = context.parse(EntityPropertyImpl.class);

        // TODO: find a way to do this better & rewrite this mess

        if (!npc.getType().getAllowedProperties().contains(property))
            context.halt(Component.text(
                    "Property " + property.getName() + " not allowed for npc type " + npc.getType().getName(),
                    NamedTextColor.RED));
        if (!property.isPlayerModifiable())
            context.halt(Component.text("This property is not modifiable by players", NamedTextColor.RED));
        Class<?> type = property.getType();
        Object value = null;
        String valueName = null;
        if (type == ItemStack.class) {
            org.bukkit.inventory.ItemStack bukkitStack = null;

            if (context.argSize() > 0) {
                try {
                    String itemIdArg = context.popString();
                    if (ItemsAdderIntegration.isItemsAdderItemId(itemIdArg)) {
                        bukkitStack = ItemsAdderIntegration.getItemsAdderItem(itemIdArg);
                        if (bukkitStack != null) {
                            value = SpigotConversionUtil.fromBukkitItemStack(bukkitStack);
                            valueName = itemIdArg;
                        } else {
                            Player player = context.ensureSenderIsPlayer();
                            org.bukkit.inventory.ItemStack heldItem = player.getInventory().getItemInHand();
                            if (heldItem != null && heldItem.getAmount() > 0) {
                                String heldItemId = ItemsAdderIntegration.getItemsAdderItemId(heldItem);
                                if (heldItemId != null) {
                                    context.halt(Component.text(
                                            "Items Adder item '" + itemIdArg
                                                    + "' not found! The item you're holding uses ID: " + heldItemId +
                                                    "\nTry: /npc property set " + entry.getId() + " "
                                                    + property.getName() + " " + heldItemId,
                                            NamedTextColor.RED));
                                } else {
                                    context.halt(Component.text(
                                            "Items Adder item '" + itemIdArg
                                                    + "' not found! Use /ia list to see available items.",
                                            NamedTextColor.RED));
                                }
                            } else {
                                context.halt(Component.text(
                                        "Items Adder item '" + itemIdArg
                                                + "' not found! Hold an Items Adder item and run this command without the ID to see the correct format.",
                                        NamedTextColor.RED));
                            }
                            return;
                        }
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().warning(
                            "[ZNPCsPlus] PropertySetCommand: Exception while processing argument: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            if (value == null) {
                Player player = context.ensureSenderIsPlayer();
                bukkitStack = player.getInventory().getItemInHand();
                if (bukkitStack == null || bukkitStack.getAmount() == 0) {
                    value = null;
                    valueName = "EMPTY";
                } else {
                    value = SpigotConversionUtil.fromBukkitItemStack(bukkitStack);
                    valueName = bukkitStack.toString();
                }
            }
        } else if (type == NamedColor.class && context.argSize() < 1 && npc.getProperty(property) != null) {
            value = null;
            valueName = "NONE";
        } else if (type == Color.class && context.argSize() < 1 && npc.getProperty(property) != null) {
            value = Color.BLACK;
            valueName = "NONE";
        } else if (type == ParrotVariant.class && context.argSize() < 1 && npc.getProperty(property) != null) {
            value = null;
            valueName = "NONE";
        } else if (type == BlockState.class) {
            String inputType = context.popString().toLowerCase();
            switch (inputType) {
                case "hand":
                    org.bukkit.inventory.ItemStack bukkitStack = context.ensureSenderIsPlayer().getInventory()
                            .getItemInHand();
                    if (bukkitStack.getAmount() == 0) {
                        value = new BlockState(0);
                        valueName = "EMPTY";
                    } else {
                        WrappedBlockState blockState = StateTypes.getByName(bukkitStack.getType().name().toLowerCase())
                                .createBlockState();
                        // WrappedBlockState blockState =
                        // WrappedBlockState.getByString(bukkitStack.getType().name().toLowerCase());
                        value = new BlockState(blockState.getGlobalId());
                        valueName = bukkitStack.toString();
                    }
                    break;
                case "looking_at":

                    // TODO

                    value = new BlockState(0);
                    valueName = "EMPTY";
                    break;
                case "block":
                    context.ensureArgsNotEmpty();
                    WrappedBlockState blockState = WrappedBlockState.getByString(context.popString());
                    value = new BlockState(blockState.getGlobalId());
                    valueName = blockState.toString();
                    break;
                default:
                    context.send(
                            Component.text("Invalid input type " + inputType + ", must be hand, looking_at, or block",
                                    NamedTextColor.RED));
                    return;
            }
        } else if (type == SpellType.class) {
            if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_13)) {
                value = context.parse(type);
                valueName = String.valueOf(value);
                if (((SpellType) value).ordinal() > 3) {
                    context.send(Component.text("Spell type " + valueName + " is not supported on this version",
                            NamedTextColor.RED));
                    return;
                }
            } else {
                value = context.parse(type);
                valueName = String.valueOf(value);
            }
        } else if (type == NpcEntryImpl.class) {
            value = context.parse(type);
            valueName = value == null ? "NONE" : ((NpcEntryImpl) value).getId();
        } else if (type == Vector3i.class) {
            value = context.parse(type);
            valueName = value == null ? "NONE" : ((Vector3i) value).toPrettyString();
        } else if (property instanceof AttributeProperty) {
            value = context.parse(type);
            if ((double) value < ((AttributeProperty) property).getMinValue()
                    || (double) value > ((AttributeProperty) property).getMaxValue()) {
                double sanitizedValue = ((AttributeProperty) property).sanitizeValue((double) value);
                context.send(Component.text("WARNING: Value " + value + " is out of range for property "
                        + property.getName() + ", setting to " + sanitizedValue, NamedTextColor.YELLOW));
                value = sanitizedValue;
            }
            valueName = String.valueOf(value);
        } else {
            try {
                value = context.parse(type);
                valueName = String.valueOf(value);
            } catch (NullPointerException e) {
                context.send(Component.text(
                        "An error occurred while trying to parse the value. Please report this to the plugin author.",
                        NamedTextColor.RED));
                e.printStackTrace();
                return;
            }
        }

        npc.UNSAFE_setProperty(property, value);
        if (type == Component.class && value != null) {
            context.send(Component
                    .text("Set property " + property.getName() + " for NPC " + entry.getId() + " to ",
                            NamedTextColor.GREEN)
                    .append((Component) value));
        } else {
            context.send(Component.text(
                    "Set property " + property.getName() + " for NPC " + entry.getId() + " to " + valueName,
                    NamedTextColor.GREEN));
        }
    }

    @Override
    public List<String> suggest(CommandContext context) throws CommandExecutionException {
        if (context.argSize() == 1)
            return context.suggestCollection(npcRegistry.getModifiableIds());
        if (context.argSize() == 2)
            return context.suggestStream(context.suggestionParse(0, NpcEntryImpl.class)
                    .getNpc().getType().getAllowedProperties().stream().map(EntityProperty::getName));
        if (context.argSize() >= 3) {
            EntityPropertyImpl<?> property = context.suggestionParse(1, EntityPropertyImpl.class);
            Class<?> type = property.getType();
            if (type == Vector3f.class && context.argSize() <= 5)
                return context.suggestLiteral("0", "0.0");
            if (context.argSize() == 3) {
                if (type == Boolean.class)
                    return context.suggestLiteral("true", "false");
                if (type == NamedColor.class)
                    return context.suggestEnum(NamedColor.values());
                if (type == Color.class)
                    return context.suggestLiteral("0x0F00FF", "#FFFFFF");
                if (type == BlockState.class)
                    return context.suggestLiteral("hand", "looking_at", "block");
                if (type == SpellType.class)
                    return PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_13)
                            ? context.suggestEnum(Arrays.stream(SpellType.values())
                                    .filter(spellType -> spellType.ordinal() <= 3).toArray(SpellType[]::new))
                            : context.suggestEnum(SpellType.values());

                if (type == Vector3i.class) {
                    if (context.getSender() instanceof Player) {
                        Player player = (Player) context.getSender();
                        Block targetBlock = player.getTargetBlock(Collections.singleton(Material.AIR), 5);
                        if (targetBlock.getType().equals(Material.AIR))
                            return Collections.emptyList();
                        return context.suggestLiteral(
                                targetBlock.getX() + "",
                                targetBlock.getX() + " " + targetBlock.getY(),
                                targetBlock.getX() + " " + targetBlock.getY() + " " + targetBlock.getZ());
                    }
                }
                // Suggest enum values directly
                if (type.isEnum()) {
                    return context.suggestEnum((Enum<?>[]) type.getEnumConstants());
                }

                if (type == ItemStack.class) {
                    // Suggest Items Adder item IDs if Items Adder is loaded
                    if (ItemsAdderIntegration.isLoaded()) {
                        try {
                            String currentArg = context.argSize() > 2 ? context.suggestionParse(2, String.class) : "";
                            if (currentArg != null && !currentArg.isEmpty()) {
                                // User is typing, suggest matching item IDs
                                List<String> suggestions = ItemsAdderIntegration.getItemIdsMatching(currentArg);
                                if (!suggestions.isEmpty()) {
                                    return suggestions;
                                }
                                // If no matches but they typed a namespace, suggest items from that namespace
                                if (currentArg.contains(":") && !currentArg.endsWith(":")) {
                                    String[] parts = currentArg.split(":", 2);
                                    if (parts.length == 2) {
                                        String namespace = parts[0];
                                        String itemPrefix = parts[1];
                                        List<String> namespaceItems = ItemsAdderIntegration
                                                .getItemIdsForNamespace(namespace)
                                                .stream()
                                                .filter(id -> id.toLowerCase().endsWith(":" + itemPrefix.toLowerCase())
                                                        ||
                                                        id.toLowerCase().contains(":" + itemPrefix.toLowerCase()))
                                                .limit(50)
                                                .collect(java.util.stream.Collectors.toList());
                                        if (!namespaceItems.isEmpty()) {
                                            return namespaceItems;
                                        }
                                    }
                                } else if (currentArg.endsWith(":")) {
                                    // User typed namespace:, suggest items from that namespace
                                    String namespace = currentArg.substring(0, currentArg.length() - 1);
                                    return ItemsAdderIntegration.getItemIdsForNamespace(namespace)
                                            .stream()
                                            .limit(50)
                                            .collect(java.util.stream.Collectors.toList());
                                }
                            } else {
                                // No input yet, suggest namespaces
                                return ItemsAdderIntegration.getNamespaces().stream()
                                        .map(ns -> ns + ":")
                                        .collect(java.util.stream.Collectors.toList());
                            }
                        } catch (Exception e) {
                            // If parsing fails, just suggest namespaces
                            return ItemsAdderIntegration.getNamespaces().stream()
                                    .map(ns -> ns + ":")
                                    .collect(java.util.stream.Collectors.toList());
                        }
                    }
                    // If Items Adder not loaded, suggest common namespaces
                    return context.suggestLiteral("iasurvival:", "itemsadder:", "custom:");
                }
            } else if (context.argSize() == 4) {
                if (type == BlockState.class) {
                    // TODO: suggest block with nbt like minecraft setblock command
                    return context.suggestionParse(2, String.class).equals("block")
                            ? context.suggestStream(StateTypes.values().stream().map(StateType::getName))
                            : Collections.emptyList();
                }
                if (type == Vector3i.class) {
                    if (context.getSender() instanceof Player) {
                        Player player = (Player) context.getSender();
                        Block targetBlock = player.getTargetBlock(Collections.singleton(Material.AIR), 5);
                        if (targetBlock.getType().equals(Material.AIR))
                            return Collections.emptyList();
                        return context.suggestLiteral(
                                targetBlock.getY() + "",
                                targetBlock.getY() + " " + targetBlock.getZ());
                    }
                }
            } else if (context.argSize() == 5) {
                if (type == Vector3i.class) {
                    if (context.getSender() instanceof Player) {
                        Player player = (Player) context.getSender();
                        Block targetBlock = player.getTargetBlock(Collections.singleton(Material.AIR), 5);
                        if (targetBlock.getType().equals(Material.AIR))
                            return Collections.emptyList();
                        return context.suggestLiteral(targetBlock.getZ() + "");
                    }
                }
            }
        }
        return Collections.emptyList();
    }
}

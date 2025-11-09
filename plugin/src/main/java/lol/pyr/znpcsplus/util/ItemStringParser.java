package lol.pyr.znpcsplus.util;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import lol.pyr.znpcsplus.reflection.ReflectionBuilder;
import lol.pyr.znpcsplus.reflection.ReflectionLazyLoader;
import lol.pyr.znpcsplus.reflection.ReflectionPackage;
import org.bukkit.Material;

import java.lang.reflect.Method;

public class ItemStringParser {
    private static final ReflectionLazyLoader<Method> MOJANGSON_PARSE_METHOD = new ReflectionLazyLoader<Method>(
            new ReflectionBuilder(ReflectionPackage.MINECRAFT)
                    .withClassName("nbt.MojangsonParser")
                    .withMethodName("parse")
                    .withParameterTypes(String.class)
                    .setStrict(false)
    ) {
        @Override
        protected Method load() throws Exception {
            for (String className : possibleClassNames) {
                try {
                    Class<?> clazz = Class.forName(className);
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.getName().equals("parse") || method.getName().equals("a")) {
                            Class<?>[] params = method.getParameterTypes();
                            if (params.length == 1 && params[0] == String.class) {
                                method.setAccessible(true);
                                return method;
                            }
                        }
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
            throw new ClassNotFoundException("MojangsonParser.parse not found");
        }
    };

    public static ItemStack parseItemString(String itemString) {
        if (itemString == null || itemString.isEmpty()) {
            return null;
        }

        String trimmed = itemString.trim();
        int bracketIndex = trimmed.indexOf('[');
        
        if (bracketIndex == -1) {
            Material material = Material.matchMaterial(trimmed);
            if (material != null && material != Material.AIR) {
                return SpigotConversionUtil.fromBukkitItemStack(new org.bukkit.inventory.ItemStack(material));
            }
            return null;
        }

        String itemPart = trimmed.substring(0, bracketIndex);
        String nbtPart = extractNbtData(trimmed, bracketIndex);
        
        Material material = Material.matchMaterial(itemPart);
        if (material == null || material == Material.AIR) {
            return null;
        }

        try {
            Method parseMethod = MOJANGSON_PARSE_METHOD.get();
            Object nbtTag = parseMethod.invoke(null, "{" + nbtPart + "}");
            
            Class<?> craftItemStackClass = Class.forName(ReflectionPackage.BUKKIT + ".inventory.CraftItemStack");
            Method asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", org.bukkit.inventory.ItemStack.class);
            Object nmsItemStack = asNMSCopyMethod.invoke(null, new org.bukkit.inventory.ItemStack(material));
            
            Method setTagMethod = nmsItemStack.getClass().getMethod("setTag", nbtTag.getClass());
            setTagMethod.invoke(nmsItemStack, nbtTag);
            
            Method asBukkitCopyMethod = craftItemStackClass.getMethod("asBukkitCopy", nmsItemStack.getClass());
            org.bukkit.inventory.ItemStack bukkitStack = (org.bukkit.inventory.ItemStack) asBukkitCopyMethod.invoke(null, nmsItemStack);
            
            return SpigotConversionUtil.fromBukkitItemStack(bukkitStack);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractNbtData(String itemString, int bracketIndex) {
        int depth = 0;
        int start = bracketIndex + 1;
        for (int i = start; i < itemString.length(); i++) {
            char c = itemString.charAt(i);
            if (c == '[' || c == '{') {
                depth++;
            } else if (c == ']') {
                if (depth == 0) {
                    return itemString.substring(start, i);
                }
                depth--;
            } else if (c == '}') {
                depth--;
            }
        }
        return itemString.substring(start, itemString.length() - 1);
    }
}

package me.kismeria.origins.core;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hides the equipment of masked entities (a rogue in stealth) from every other client.
 * The server resends real equipment on its own (entity comes into view, equipment detection,
 * interactions), so faking it once with sendEquipmentChange is not enough: every outgoing
 * ClientboundSetEquipmentPacket for a masked entity is rewritten to empty slots, including the
 * ones inside bundles (spawn pairing data).
 */
public final class EquipmentMask {
    private static final Key LISTENER_KEY = Key.key("smporigins", "equipment_mask");
    private static final String HANDLER = "smporigins_equipment_mask";

    private final Plugin plugin;
    private final Set<Integer> masked = ConcurrentHashMap.newKeySet();
    private boolean installed;

    private MethodHandle getEntity;
    private MethodHandle getSlots;
    private MethodHandle newEquipment;
    private MethodHandle pairOf;
    private MethodHandle pairFirst;
    private Object emptyStack;
    private Class<?> equipmentPacket;
    private Class<?> bundlePacket;
    private MethodHandle subPackets;
    private MethodHandle newBundle;

    public EquipmentMask(Plugin plugin) {
        this.plugin = plugin;
    }

    public void mask(Player p) {
        masked.add(p.getEntityId());
    }

    public void unmask(Player p) {
        masked.remove(p.getEntityId());
    }

    public void enable(Iterable<? extends Player> online) {
        try {
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            equipmentPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket");
            bundlePacket = Class.forName("net.minecraft.network.protocol.game.ClientboundBundlePacket");
            Class<?> itemStack = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> pair = Class.forName("com.mojang.datafixers.util.Pair");
            getEntity = l.findVirtual(equipmentPacket, "getEntity", MethodType.methodType(int.class));
            getSlots = l.findVirtual(equipmentPacket, "getSlots", MethodType.methodType(List.class));
            newEquipment = l.findConstructor(equipmentPacket, MethodType.methodType(void.class, int.class, List.class));
            pairOf = l.findStatic(pair, "of", MethodType.methodType(pair, Object.class, Object.class));
            pairFirst = l.findVirtual(pair, "getFirst", MethodType.methodType(Object.class));
            emptyStack = itemStack.getField("EMPTY").get(null);
            subPackets = l.findVirtual(bundlePacket, "subPackets", MethodType.methodType(Iterable.class));
            newBundle = l.findConstructor(bundlePacket, MethodType.methodType(void.class, Iterable.class));

            Class<?> holder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            Class<?> listener = Class.forName("io.papermc.paper.network.ChannelInitializeListener");
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "afterInitChannel" -> {
                    inject((Channel) args[0]);
                    yield null;
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "SmpOrigins equipment mask";
                default -> null;
            };
            Object proxy = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, handler);
            holder.getMethod("addListener", Key.class, listener).invoke(null, LISTENER_KEY, proxy);
            installed = true;
            // players already online (plugin reload)
            for (Player p : online) {
                Channel ch = channelOf(p);
                if (ch != null) inject(ch);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            plugin.getLogger().warning("Скрытие брони разбойника через пакеты недоступно: " + e);
        }
    }

    public void disable(Iterable<? extends Player> online) {
        if (!installed) return;
        try {
            Class<?> holder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            holder.getMethod("removeListener", Key.class).invoke(null, LISTENER_KEY);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // shutting down
        }
        for (Player p : online) {
            Channel ch = channelOf(p);
            if (ch != null && ch.pipeline().get(HANDLER) != null) {
                ch.eventLoop().execute(() -> {
                    if (ch.pipeline().get(HANDLER) != null) ch.pipeline().remove(HANDLER);
                });
            }
        }
        masked.clear();
    }

    private void inject(Channel channel) {
        if (channel.pipeline().get("packet_handler") != null && channel.pipeline().get(HANDLER) == null) {
            channel.pipeline().addBefore("packet_handler", HANDLER, new Handler());
        }
    }

    /** CraftPlayer.getHandle().connection.connection.channel, found by type. */
    private static Channel channelOf(Player p) {
        try {
            Object handle = p.getClass().getMethod("getHandle").invoke(p);
            Object listener = fieldOfType(handle, "ServerGamePacketListenerImpl");
            Object connection = listener == null ? null : fieldOfType(listener, "Connection");
            Object channel = connection == null ? null : fieldOfType(connection, "Channel");
            return channel instanceof Channel ch ? ch : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object fieldOfType(Object owner, String simpleName) throws IllegalAccessException {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().getSimpleName().equals(simpleName)) {
                    f.setAccessible(true);
                    return f.get(owner);
                }
            }
        }
        return null;
    }

    private Object rewrite(Object packet) throws Throwable {
        if (masked.isEmpty()) return packet;
        if (equipmentPacket.isInstance(packet)) {
            int id = (int) getEntity.invoke(packet);
            if (!masked.contains(id)) return packet;
            List<?> slots = (List<?>) getSlots.invoke(packet);
            List<Object> empty = new ArrayList<>(slots.size());
            for (Object pair : slots) empty.add(pairOf.invoke(pairFirst.invoke(pair), emptyStack));
            return newEquipment.invoke(id, empty);
        }
        if (bundlePacket.isInstance(packet)) {
            boolean changed = false;
            List<Object> out = new ArrayList<>();
            for (Object sub : (Iterable<?>) subPackets.invoke(packet)) {
                Object r = rewrite(sub);
                changed |= r != sub;
                out.add(r);
            }
            return changed ? newBundle.invoke(out) : packet;
        }
        return packet;
    }

    private final class Handler extends ChannelDuplexHandler {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            Object out = msg;
            try {
                out = rewrite(msg);
            } catch (Throwable ignored) {
                // never break the connection over a cosmetic feature
            }
            super.write(ctx, out, promise);
        }
    }
}

package net.pl3x.bukkit.cmdcd;

import net.pl3x.bukkit.cmdcd.configuration.Config;
import net.pl3x.bukkit.cmdcd.configuration.Lang;
import net.pl3x.purpur.event.ExecuteCommandEvent;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CmdCD extends JavaPlugin {
    private static final Map<Command, Map<UUID, Long>> COOLDOWNS = new HashMap<>();

    public static void addCooldown(Command command, UUID uuid, int seconds) {
        COOLDOWNS.computeIfAbsent(command, k -> new HashMap<>())
                .put(uuid, System.currentTimeMillis() + (seconds * 1000));
    }

    public static void removeCooldown(Command command, UUID uuid) {
        Map<UUID, Long> cooldowns = COOLDOWNS.get(command);
        if (cooldowns != null) {
            cooldowns.remove(uuid);
        }
    }

    public static boolean isOnCooldown(Command command, UUID uuid) {
        Map<UUID, Long> cooldowns = COOLDOWNS.get(command);
        return cooldowns != null && cooldowns.containsKey(uuid);
    }

    public static int getSecondsRemaining(Command command, UUID uuid) {
        Map<UUID, Long> cooldowns = COOLDOWNS.get(command);
        if (cooldowns == null) {
            return 0;
        }
        Long expireTime = cooldowns.get(uuid);
        if (expireTime == null) {
            return 0;
        }
        return (int) ((expireTime - System.currentTimeMillis()) / 1000);
    }

    public static Map<Command, Long> getCooldowns(UUID uuid) {
        Map<Command, Long> allCooldowns = new HashMap<>();
        for (Map.Entry<Command, Map<UUID, Long>> entry : COOLDOWNS.entrySet()) {
            Long expire = entry.getValue().get(uuid);
            if (expire != null) {
                allCooldowns.put(entry.getKey(), expire);
            }
        }
        return allCooldowns;
    }

    @Override
    public void onEnable() {
        Config.reload(this);
        Lang.reload(this);

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<Command, Map<UUID, Long>>> iter = COOLDOWNS.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<Command, Map<UUID, Long>> entry = iter.next();
                    Map<UUID, Long> cooldowns = entry.getValue();
                    for (Long expire : cooldowns.values()) {
                        if (expire <= now) {
                            iter.remove();
                        }
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            public void onPlayerCommand(ExecuteCommandEvent event) {
                if (event.getSender() instanceof Player) {
                    int seconds = getSecondsRemaining(event.getCommand(), ((Player) event.getSender()).getUniqueId());
                    if (seconds > 0) {
                        Lang.send(event.getSender(), Lang.YOU_ARE_ON_COOLDOWN
                                .replace("{seconds}", String.valueOf(seconds))
                                .replace("{remaining}", formatRemaining(seconds))
                        );
                        event.setCancelled(true);
                    }
                }
            }
        }, this);
    }

    public static String formatRemaining(int seconds) {
        long hours = TimeUnit.SECONDS.toHours(seconds);
        seconds -= TimeUnit.HOURS.toSeconds(hours);

        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        seconds -= TimeUnit.MINUTES.toSeconds(minutes);

        StringBuilder msg = new StringBuilder();
        if (hours != 0) {
            msg.append(hours).append(" hour");
            if (hours != 1) {
                msg.append("s");
            }
        }
        if (minutes != 0) {
            if (!msg.toString().isEmpty()) {
                msg.append(" ");
            }
            msg.append(minutes).append(" minute");
            if (minutes != 1) {
                msg.append("s");
            }
        }
        if (seconds != 0) {
            if (!msg.toString().isEmpty()) {
                msg.append(" ");
            }
            msg.append(seconds).append(" second");
            if (seconds != 1) {
                msg.append("s");
            }
        }

        return msg.toString();
    }
}

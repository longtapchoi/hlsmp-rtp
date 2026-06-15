package de.elivb.donutRTP.Manager;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import de.elivb.donutRTP.Hex;
import de.elivb.donutRTP.RTP;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RTPZoneManager implements Listener {
   private final RTP plugin;
   private final RTPManager rtpManager;
   private final Map<String, RTPZone> rtpZones;
   private final Map<String, ZoneGlobalTimer> zoneTimers;
   private final Map<UUID, String> playerZone;
   private final Random random;
   private RTPExpansion expansion;

   public RTPZoneManager(RTP plugin, RTPManager rtpManager) {
      this.plugin = plugin;
      this.rtpManager = rtpManager;
      this.rtpZones = new HashMap();
      this.zoneTimers = new ConcurrentHashMap();
      this.playerZone = new ConcurrentHashMap();
      this.random = new Random();
      this.loadRTPZones();
      this.registerPlaceholderAPI();
      this.registerEvents();
      this.startAllZoneTimers();
   }

   private void registerEvents() {
      this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
      this.plugin.getLogger().info("RTPZoneManager events registered!");
   }

   private void registerPlaceholderAPI() {
      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
         this.expansion = new RTPExpansion();
         this.expansion.register();
      }

   }

   private void loadRTPZones() {
      this.rtpZones.clear();
      if (this.plugin.getConfig().contains("rtp-zones")) {
         ConfigurationSection zonesSection = this.plugin.getConfig().getConfigurationSection("rtp-zones");
         if (zonesSection != null) {
            for(String zoneId : zonesSection.getKeys(false)) {
               String path = "rtp-zones." + zoneId;
               boolean enabled = this.plugin.getConfig().getBoolean(path + ".enabled", true);
               if (enabled) {
                  String zoneRegion = this.plugin.getConfig().getString(path + ".zone-region");
                  String zoneWorld = this.plugin.getConfig().getString(path + ".zone-world");
                  int cooldownTime = this.plugin.getConfig().getInt(path + ".cooldown-time", 60);
                  int minimumPlayers = this.plugin.getConfig().getInt(path + ".minimum-players", 1);
                  List<String> rtpWorlds = this.plugin.getConfig().getStringList(path + ".rtp-worlds");
                  if (zoneRegion != null && zoneWorld != null && !rtpWorlds.isEmpty()) {
                     RTPZone zone = new RTPZone(zoneId, zoneRegion, zoneWorld, cooldownTime, minimumPlayers, rtpWorlds);
                     this.rtpZones.put(zoneId, zone);
                  }
               }
            }

         }
      }
   }

   private void startAllZoneTimers() {
      for(RTPZone zone : this.rtpZones.values()) {
         if (!this.zoneTimers.containsKey(zone.getId())) {
            this.startGlobalTimer(zone);
         }
      }

   }

   private void startGlobalTimer(RTPZone zone) {
      String zoneId = zone.getId();
      int cooldownSeconds = zone.getCooldownTime();
      int[] remaining = new int[]{cooldownSeconds};
      ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, (scheduledTask) -> {
         if (remaining[0] <= 0) {
            ZoneGlobalTimer timer = (ZoneGlobalTimer)this.zoneTimers.get(zoneId);
            if (timer != null) {
               for(UUID pid : timer.playersInZone) {
                  Player p = Bukkit.getPlayer(pid);
                  if (p != null && p.isOnline()) {
                     this.teleportPlayerFromZone(p, zone);
                  }
               }
            }

            remaining[0] = zone.getCooldownTime();
            ZoneGlobalTimer t = (ZoneGlobalTimer)this.zoneTimers.get(zoneId);
            if (t != null) {
               t.remainingSeconds = remaining[0];
            }
         } else {
            int var10002 = remaining[0]--;
            ZoneGlobalTimer timer = (ZoneGlobalTimer)this.zoneTimers.get(zoneId);
            if (timer != null) {
               timer.remainingSeconds = remaining[0];
            }
         }

      }, 20L, 20L);
      ZoneGlobalTimer newTimer = new ZoneGlobalTimer(zone, task, cooldownSeconds);
      this.zoneTimers.put(zoneId, newTimer);
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      this.updatePlayerZone(event.getPlayer());
   }

   @EventHandler
   public void onPlayerTeleport(PlayerTeleportEvent event) {
      Player player = event.getPlayer();
      player.getScheduler().runDelayed(this.plugin, (scheduledTask) -> this.updatePlayerZone(player), (Runnable)null, 2L);
   }

   @EventHandler
   public void onPlayerMove(PlayerMoveEvent event) {
      Player player = event.getPlayer();
      Location from = event.getFrom();
      Location to = event.getTo();
      if (to != null) {
         this.updatePlayerZone(player);
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      UUID playerId = event.getPlayer().getUniqueId();
      String zoneId = (String)this.playerZone.remove(playerId);
      if (zoneId != null) {
         ZoneGlobalTimer timer = (ZoneGlobalTimer)this.zoneTimers.get(zoneId);
         if (timer != null) {
            timer.playersInZone.remove(playerId);
         }
      }

   }

   private void updatePlayerZone(Player player) {
      UUID playerId = player.getUniqueId();
      RTPZone zone = this.getZoneAtLocation(player);
      String newZoneId = zone != null ? zone.getId() : null;
      String oldZoneId = (String)this.playerZone.get(playerId);
      if (!Objects.equals(newZoneId, oldZoneId)) {
         if (oldZoneId != null) {
            ZoneGlobalTimer oldTimer = (ZoneGlobalTimer)this.zoneTimers.get(oldZoneId);
            if (oldTimer != null) {
               oldTimer.playersInZone.remove(playerId);
               String leaveMessage = this.plugin.getLangConfig().getString("messages.zone-leave-message", "&fYou left the &c%zone% Zone&f!");
               leaveMessage = leaveMessage.replace("%zone%", zone != null ? zone.getZoneRegion() : oldZoneId);
               player.sendMessage(Hex.translateAllColorCodes(leaveMessage));
            }
         }

         if (newZoneId != null) {
            this.playerZone.put(playerId, newZoneId);
            ZoneGlobalTimer timer = (ZoneGlobalTimer)this.zoneTimers.get(newZoneId);
            if (timer != null) {
               timer.playersInZone.add(playerId);
               String enterMessage = this.plugin.getLangConfig().getString("messages.zone-enter-message", "&fYou entered the &c%zone% Zone&f!");
               enterMessage = enterMessage.replace("%zone%", zone.getZoneRegion());
               player.sendMessage(Hex.translateAllColorCodes(enterMessage));
            } else {
               RTPZone newZone = this.getRTPZone(newZoneId);
               if (newZone != null) {
                  this.startGlobalTimer(newZone);
                  ZoneGlobalTimer newTimer = (ZoneGlobalTimer)this.zoneTimers.get(newZoneId);
                  if (newTimer != null) {
                     newTimer.playersInZone.add(playerId);
                  }
               }
            }
         } else {
            this.playerZone.remove(playerId);
         }

      }
   }

   private void teleportPlayerFromZone(Player player, RTPZone zone) {
      List<String> worlds = zone.getRtpWorlds();
      if (!worlds.isEmpty()) {
         String worldName = (String)worlds.get(this.random.nextInt(worlds.size()));
         World targetWorld = Bukkit.getWorld(worldName);
         if (targetWorld != null) {
            int arenaDistance = this.plugin.getConfig().getInt("rtp-zones." + zone.getId() + ".arena-distance", 10);
            int glowingDuration = this.plugin.getConfig().getInt("rtp-zones." + zone.getId() + ".glowing-duration", 6);
            this.rtpManager.findSafeLocationAsync(targetWorld).thenAccept((centerLocation) -> {
               if (centerLocation == null) {
                  player.getScheduler().run(this.plugin, (task) -> {
                  }, (Runnable)null);
               } else {
                  this.findLocationInRadius(player, targetWorld, centerLocation, arenaDistance, zone, glowingDuration);
               }
            });
         }
      }
   }

   private void findLocationInRadius(Player player, World world, Location center, int radius, RTPZone zone, int glowingDuration) {
      for(int attempt = 0; attempt < 20; ++attempt) {
         int offsetX = this.random.nextInt(radius * 2) - radius;
         int offsetZ = this.random.nextInt(radius * 2) - radius;
         int x = center.getBlockX() + offsetX;
         int z = center.getBlockZ() + offsetZ;
         int y = world.getHighestBlockYAt(x, z) + 1;
         if (y > world.getMinHeight() && y < world.getMaxHeight()) {
            Location location = new Location(world, (double)x + (double)0.5F, (double)y, (double)z + (double)0.5F);
            if (this.rtpManager.isLocationSafe(location)) {
               player.getScheduler().run(this.plugin, (task) -> player.teleportAsync(location).thenAccept((success) -> {
                     if (success) {
                        String teleportedMessage = this.plugin.getLangConfig().getString("messages.teleported-message", "&aSuccessfully teleported");
                        this.rtpManager.playSound(player, "teleport_success");
                        player.sendMessage(Hex.translateAllColorCodes(teleportedMessage));
                        this.applyGlowingEffect(player, glowingDuration);
                     }

                  }), (Runnable)null);
               return;
            }
         }
      }

      player.getScheduler().run(this.plugin, (task) -> player.teleportAsync(center).thenAccept((success) -> {
            if (success) {
               String teleportedMessage = this.plugin.getLangConfig().getString("messages.teleported-message", "&aSuccessfully teleported");
               this.rtpManager.playSound(player, "teleport_success");
               player.sendMessage(Hex.translateAllColorCodes(teleportedMessage));
               this.applyGlowingEffect(player, glowingDuration);
            }

         }), (Runnable)null);
   }

   private void applyGlowingEffect(Player player, int glowingDuration) {
      if (glowingDuration > 0) {
         player.getScheduler().run(this.plugin, (task) -> {
            player.setGlowing(true);

            try {
               PotionEffectType glow = PotionEffectType.GLOWING;
               if (glow != null) {
                  player.addPotionEffect(new PotionEffect(glow, glowingDuration * 20, 0, true, false, true));
               }
            } catch (Exception var5) {
            }

            player.getScheduler().runDelayed(this.plugin, (removeTask) -> {
               if (player.isOnline()) {
                  player.setGlowing(false);

                  try {
                     PotionEffectType glow = PotionEffectType.GLOWING;
                     if (glow != null) {
                        player.removePotionEffect(glow);
                     }
                  } catch (Exception var3) {
                  }
               }

            }, (Runnable)null, (long)glowingDuration * 20L);
         }, (Runnable)null);
      }
   }

   public int getRemainingSeconds(Player player, String zoneId) {
      ZoneGlobalTimer timer = (ZoneGlobalTimer)this.zoneTimers.get(zoneId);
      return timer != null ? Math.max(0, timer.remainingSeconds) : 0;
   }

   private boolean hasBypass(Player player) {
      return player.isOp() || player.hasPermission("rtp.bypass");
   }

   public Map<String, RTPZone> getRTPZones() {
      return this.rtpZones;
   }

   public RTPZone getRTPZone(String zoneId) {
      return (RTPZone)this.rtpZones.get(zoneId);
   }

   public RTPZone getZoneAtLocation(Player player) {
      Location loc = player.getLocation();

      for(RTPZone zone : this.rtpZones.values()) {
         if (zone.isPlayerInZone(this.plugin, player, loc)) {
            return zone;
         }
      }

      return null;
   }

   public void reload() {
      for(ZoneGlobalTimer timer : this.zoneTimers.values()) {
         if (timer.task != null) {
            timer.task.cancel();
         }
      }

      this.zoneTimers.clear();
      this.playerZone.clear();
      this.loadRTPZones();
      this.startAllZoneTimers();
      if (this.expansion != null) {
         this.expansion.close();
         this.expansion = new RTPExpansion();
         this.expansion.register();
      }

   }

   private static class ZoneGlobalTimer {
      final RTPZone zone;
      final ScheduledTask task;
      int remainingSeconds;
      final Set<UUID> playersInZone;

      ZoneGlobalTimer(RTPZone zone, ScheduledTask task, int remainingSeconds) {
         this.zone = zone;
         this.task = task;
         this.remainingSeconds = remainingSeconds;
         this.playersInZone = ConcurrentHashMap.newKeySet();
      }
   }

   private class RTPExpansion extends PlaceholderExpansion {
      public @NotNull String getIdentifier() {
         return "donutrtp";
      }

      public @NotNull String getAuthor() {
         return "EliVB";
      }

      public @NotNull String getVersion() {
         return RTPZoneManager.this.plugin.getDescription().getVersion();
      }

      public boolean persist() {
         return true;
      }

      public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
         if (player == null) {
            return "";
         } else if (identifier.startsWith("zone_countdown_")) {
            String zoneId = identifier.substring("zone_countdown_".length());
            int remaining = RTPZoneManager.this.getRemainingSeconds(player, zoneId);
            return String.valueOf(remaining);
         } else if (identifier.startsWith("zone_name_")) {
            String zoneId = identifier.substring("zone_name_".length());
            RTPZone zone = RTPZoneManager.this.getRTPZone(zoneId);
            return zone != null ? zone.getZoneRegion() : "unknown";
         } else if (identifier.equals("current_zone")) {
            RTPZone zone = RTPZoneManager.this.getZoneAtLocation(player);
            return zone != null ? zone.getId() : "none";
         } else if (identifier.equals("current_zone_cooldown")) {
            RTPZone zone = RTPZoneManager.this.getZoneAtLocation(player);
            return zone != null ? String.valueOf(RTPZoneManager.this.getRemainingSeconds(player, zone.getId())) : "0";
         } else {
            return null;
         }
      }

      public boolean register() {
         return super.register();
      }

      public void close() {
         this.unregister();
      }
   }

   public static class RTPZone {
      private final String id;
      private final String zoneRegion;
      private final String zoneWorld;
      private final int cooldownTime;
      private final int minimumPlayers;
      private final List<String> rtpWorlds;

      public RTPZone(String id, String zoneRegion, String zoneWorld, int cooldownTime, int minimumPlayers, List<String> rtpWorlds) {
         this.id = id;
         this.zoneRegion = zoneRegion;
         this.zoneWorld = zoneWorld;
         this.cooldownTime = cooldownTime;
         this.minimumPlayers = minimumPlayers;
         this.rtpWorlds = rtpWorlds;
      }

      public String getId() {
         return this.id;
      }

      public String getZoneRegion() {
         return this.zoneRegion;
      }

      public String getZoneWorld() {
         return this.zoneWorld;
      }

      public int getCooldownTime() {
         return this.cooldownTime;
      }

      public int getMinimumPlayers() {
         return this.minimumPlayers;
      }

      public List<String> getRtpWorlds() {
         return this.rtpWorlds;
      }

      public boolean isPlayerInZone(RTP plugin, Player player, Location loc) {
         if (!loc.getWorld().getName().equals(this.zoneWorld)) {
            return false;
         } else {
            try {
               RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
               com.sk89q.worldedit.world.World editWorld = BukkitAdapter.adapt(loc.getWorld());
               RegionManager regionManager = container.get(editWorld);
               if (regionManager == null) {
                  return false;
               } else {
                  BlockVector3 vector = BukkitAdapter.asBlockVector(loc);
                  ApplicableRegionSet regions = regionManager.getApplicableRegions(vector);
                  return regions.getRegions().stream().anyMatch((r) -> r.getId().equalsIgnoreCase(this.zoneRegion));
               }
            } catch (Exception var9) {
               return false;
            }
         }
      }
   }
}

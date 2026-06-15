package de.elivb.donutRTP.gui;

import de.elivb.donutRTP.Hex;
import de.elivb.donutRTP.RTP;
import de.elivb.donutRTP.Manager.RTPManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RTPGui implements Listener {
   private final RTP plugin;
   private final RTPManager rtpManager;
   private FileConfiguration guiConfig;
   private final Map<UUID, Object> playerTasks;
   private final Map<UUID, Location> startLocations;
   private final Set<UUID> teleportingPlayers;
   private final Map<UUID, Integer> remainingCountdowns;

   public RTPGui(RTP plugin, RTPManager rtpManager) {
      this.plugin = plugin;
      this.rtpManager = rtpManager;
      this.playerTasks = new ConcurrentHashMap();
      this.startLocations = new ConcurrentHashMap();
      this.teleportingPlayers = ConcurrentHashMap.newKeySet();
      this.remainingCountdowns = new ConcurrentHashMap();
      this.loadGuiConfig();
   }

   private void loadGuiConfig() {
      File guiFolder = new File(this.plugin.getDataFolder(), "gui");
      if (!guiFolder.exists()) {
         boolean created = guiFolder.mkdirs();
         if (!created) {
         }
      }

      File guiFile = new File(guiFolder, "rtp.yml");
      if (guiFile.exists()) {
         this.guiConfig = YamlConfiguration.loadConfiguration(guiFile);
      } else {
         this.createDefaultGuiConfig(guiFile);
         this.guiConfig = YamlConfiguration.loadConfiguration(guiFile);
      }

   }

   private void createDefaultGuiConfig(File guiFile) {
      try {
         YamlConfiguration config = new YamlConfiguration();
         config.set("title", "&8ᴅɪ ᴄʜᴜʏểɴ ɴɢẫᴜ ɴʜɪêɴ");
         config.set("rows", 3);
         config.set("worlds.overworld.slot", 11);
         config.set("worlds.overworld.world", "world");
         config.set("worlds.overworld.material", "GRASS_BLOCK");
         config.set("worlds.overworld.name", "&#00FF89ᴛʜế ɢɪớɪ ɢốᴄ");
         config.set("worlds.overworld.lore", Arrays.asList("&fNhấn để di chuyển ngẫu nhiên", "", "&7Người chơi (&#00A0FC%players%&7)"));
         config.set("worlds.overworld.permission", "");
         config.set("worlds.nether.slot", 13);
         config.set("worlds.nether.world", "world_nether");
         config.set("worlds.nether.material", "NETHERRACK");
         config.set("worlds.nether.name", "&#00FF89ɴᴇᴛʜᴇʀ");
         config.set("worlds.nether.lore", Arrays.asList("&fNhấn để di chuyển ngẫu nhiên", "", "&7Người chơi (&#00A0FC%players%&7)"));
         config.set("worlds.nether.permission", "");
         config.set("worlds.end.slot", 15);
         config.set("worlds.end.world", "world_the_end");
         config.set("worlds.end.material", "END_STONE");
         config.set("worlds.end.name", "&#00FF89ᴇɴᴅ");
         config.set("worlds.end.lore", Arrays.asList("&fNhấn để di chuyển ngẫu nhiên", "", "&7Người chơi (&#00A0FC%players%&7)"));
         config.set("worlds.end.permission", "");
         config.save(guiFile);
      } catch (Exception var3) {
      }

   }

   public void openRTPGui(Player player) {
      if (this.teleportingPlayers.contains(player.getUniqueId())) {
         int remaining = this.remainingCountdowns.getOrDefault(player.getUniqueId(), 0);
         player.sendMessage(this.rtpManager.getMessage("teleport-on-cooldown-message", "%wait_time%", String.valueOf(remaining)));
      } else {
         try {
            String title = Hex.translateAllColorCodes(this.guiConfig.getString("title", "&8ᴅɪ ᴄʜᴜʏểɴ ɴɢẫᴜ ɴʜɪêɴ"));
            int rows = this.guiConfig.getInt("rows", 3);
            Inventory gui = Bukkit.createInventory((InventoryHolder)null, rows * 9, title);
            if (this.guiConfig.contains("worlds")) {
               for(String worldKey : this.guiConfig.getConfigurationSection("worlds").getKeys(false)) {
                  this.setupGuiItem(gui, player, worldKey);
               }
            }

            player.openInventory(gui);
         } catch (Exception var7) {
         }

      }
   }

   private void setupGuiItem(Inventory gui, Player player, String worldKey) {
      String path = "worlds." + worldKey;
      int slot = this.guiConfig.getInt(path + ".slot");
      String worldName = this.guiConfig.getString(path + ".world");
      Material material = Material.getMaterial(this.guiConfig.getString(path + ".material", "GRASS_BLOCK"));
      String itemName = Hex.translateAllColorCodes(this.guiConfig.getString(path + ".name", ""));
      String permission = this.guiConfig.getString(path + ".permission", "");
      if (permission.isEmpty() || player.hasPermission(permission)) {
         World world = Bukkit.getWorld(worldName);
         if (world != null) {
            this.rtpManager.isWorldEnabled(worldName);
            int playerCount = world.getPlayers().size();
            ItemStack item = this.createWorldItem(material, itemName, worldKey, playerCount);
            if (item != null && slot >= 0 && slot < gui.getSize()) {
               gui.setItem(slot, item);
            }

         }
      }
   }

   private ItemStack createWorldItem(Material material, String name, String worldKey, int playerCount) {
      if (material == null) {
         material = Material.GRASS_BLOCK;
      }

      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return item;
      } else {
         meta.setDisplayName(name);
         List<String> lore = new ArrayList();

         for(String line : this.guiConfig.getStringList("worlds." + worldKey + ".lore")) {
            String processedLine = line.replace("%players%", String.valueOf(playerCount));
            lore.add(Hex.translateAllColorCodes(processedLine));
         }

         meta.setLore(lore);
         item.setItemMeta(meta);
         return item;
      }
   }

   public void reloadGuiConfig() {
      this.loadGuiConfig();
   }

   private String getWorldFromSlot(int slot) {
      if (!this.guiConfig.contains("worlds")) {
         return null;
      } else {
         for(String worldKey : this.guiConfig.getConfigurationSection("worlds").getKeys(false)) {
            if (this.guiConfig.getInt("worlds." + worldKey + ".slot") == slot) {
               return this.guiConfig.getString("worlds." + worldKey + ".world");
            }
         }

         return null;
      }
   }

   private String getWorldKeyFromSlot(int slot) {
      if (!this.guiConfig.contains("worlds")) {
         return null;
      } else {
         for(String worldKey : this.guiConfig.getConfigurationSection("worlds").getKeys(false)) {
            if (this.guiConfig.getInt("worlds." + worldKey + ".slot") == slot) {
               return worldKey;
            }
         }

         return null;
      }
   }

   private boolean hasPermissionForWorld(Player player, String worldKey) {
      String permission = this.guiConfig.getString("worlds." + worldKey + ".permission", "");
      return permission.isEmpty() || player.hasPermission(permission);
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player) {
         Player player = (Player)event.getWhoClicked();
         String title = event.getView().getTitle();
         String strippedTitle = ChatColor.stripColor(title);
         String configTitle = ChatColor.stripColor(Hex.translateAllColorCodes(this.guiConfig.getString("title", "&8ᴅɪ ᴄʜᴜʏểɴ ɴɢẫᴜ ɴʜɪêɴ")));
         if (strippedTitle.equals(configTitle)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot >= 0 && slot < event.getInventory().getSize()) {
               String worldName = this.getWorldFromSlot(slot);
               String worldKey = this.getWorldKeyFromSlot(slot);
               if (worldName != null && worldKey != null) {
                  this.handleWorldClick(player, worldName, worldKey);
               }
            }
         }
      }
   }

   private void handleWorldClick(Player player, String worldName, String worldKey) {
      if (this.teleportingPlayers.contains(player.getUniqueId())) {
         int remaining = this.remainingCountdowns.getOrDefault(player.getUniqueId(), 0);
         player.sendMessage(this.rtpManager.getMessage("teleport-on-cooldown-message", "%wait_time%", String.valueOf(remaining)));
         player.closeInventory();
      } else {
         this.rtpManager.playSound(player, "button_click");
         World world = Bukkit.getWorld(worldName);
         if (world == null) {
            player.sendMessage(this.rtpManager.getMessage("no-world-permission-message"));
            player.closeInventory();
         } else if (this.hasPermissionForWorld(player, worldKey) && this.rtpManager.isWorldEnabled(worldName)) {
            if (!this.rtpManager.canUseRTP(player, world)) {
               long remaining = this.rtpManager.getRemainingCooldown(player, world);
               long seconds = Math.max(1L, remaining / 1000L);
               player.sendMessage(this.rtpManager.getMessage("teleport-on-cooldown-message", "%wait_time%", String.valueOf(seconds)));
               player.closeInventory();
            } else {
               player.closeInventory();
               this.startTeleport(player, world);
            }
         } else {
            player.sendMessage(this.rtpManager.getMessage("no-world-permission-message"));
            player.closeInventory();
         }
      }
   }

   private void startTeleport(final Player player, final World world) {
      this.rtpManager.playSound(player, "teleporting");
      final UUID playerId = player.getUniqueId();
      this.teleportingPlayers.add(playerId);
      final int teleportDelay = this.rtpManager.getWaitTime(player);
      this.remainingCountdowns.put(playerId, teleportDelay);
      double maxDistance = this.plugin.getConfig().getDouble("teleport-distance", 0.3);
      final double maxDistanceSquared = maxDistance * maxDistance;
      final Location startLocation = player.getLocation().clone();
      final String startWorldName = startLocation.getWorld().getName();
      final Object[] taskHolder = new Object[1];
      Runnable countdownTask = new Runnable() {
         int count = teleportDelay;
         boolean firstRun = true;
         Location lastValidLocation = startLocation.clone();
         String lastWorldName = startWorldName;
         boolean isCancelled = false;

         private void cancelTask() {
            if (!this.isCancelled) {
               this.isCancelled = true;
               if (taskHolder[0] != null) {
                  RTPGui.this.plugin.cancelTask(taskHolder[0]);
                  taskHolder[0] = null;
               }

               RTPGui.this.teleportingPlayers.remove(playerId);
               RTPGui.this.startLocations.remove(playerId);
               RTPGui.this.remainingCountdowns.remove(playerId);
            }
         }

         public void run() {
            if (!this.isCancelled) {
               if (this.firstRun) {
                  this.firstRun = false;
                  int currentCount = this.count;
                  RTPGui.this.plugin.runAtPlayer(player, () -> {
                     if (teleportDelay > 0) {
                        RTPGui.this.rtpManager.sendCooldownMessage(player, (long)currentCount);
                     }

                  });
               } else if (RTPGui.this.teleportingPlayers.contains(playerId) && player.isOnline()) {
                  Location currentLocation = player.getLocation();
                  String currentWorldName = currentLocation.getWorld().getName();
                  if (!currentWorldName.equals(this.lastWorldName)) {
                     this.lastWorldName = currentWorldName;
                     this.lastValidLocation = currentLocation.clone();
                  } else {
                     double deltaX = currentLocation.getX() - this.lastValidLocation.getX();
                     double deltaY = currentLocation.getY() - this.lastValidLocation.getY();
                     double deltaZ = currentLocation.getZ() - this.lastValidLocation.getZ();
                     double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                     if (distanceSquared > maxDistanceSquared) {
                        RTPGui.this.plugin.runAtPlayer(player, () -> {
                           RTPGui.this.rtpManager.playSound(player, "teleport_fail");
                           RTPGui.this.sendActionBar(player, RTPGui.this.rtpManager.getActionBar("teleporting-cancel-message"));
                           player.sendMessage(RTPGui.this.rtpManager.getMessage("teleporting-cancel-message"));
                        });
                        this.cancelTask();
                        return;
                     }

                     this.lastValidLocation = currentLocation.clone();
                  }

                  --this.count;
                  RTPGui.this.remainingCountdowns.put(playerId, Math.max(0, this.count));
                  if (this.count <= 0) {
                     RTPGui.this.performTeleport(player, world, playerId);
                     this.cancelTask();
                  } else {
                     int currentCount = this.count;
                     RTPGui.this.plugin.runAtPlayer(player, () -> {
                        RTPGui.this.rtpManager.sendCooldownMessage(player, (long)currentCount);
                        RTPGui.this.rtpManager.playSound(player, "teleporting");
                     });
                  }
               } else {
                  this.cancelTask();
               }
            }
         }
      };
      taskHolder[0] = this.plugin.runGlobalTimer(countdownTask, 1L, 20L);
   }

   private void performTeleport(Player player, World world, UUID playerId) {
      this.rtpManager.findSafeLocationAsync(world).thenAccept((safeLocation) -> this.plugin.runAtPlayer(player, () -> {
            if (safeLocation != null) {
               player.teleportAsync(safeLocation).thenAccept((success) -> {
                  if (success) {
                     this.rtpManager.playSound(player, "teleport_success");
                     this.rtpManager.setCooldown(player, world);
                     this.sendActionBar(player, this.rtpManager.getActionBar("teleported-message"));
                     player.sendMessage(this.rtpManager.getMessage("teleported-message"));
                     this.rtpManager.applyTeleportEffects(player);
                  } else {
                     this.rtpManager.playSound(player, "teleport_fail");
                     this.sendActionBar(player, this.rtpManager.getActionBar("fail-message"));
                     player.sendMessage(this.rtpManager.getMessage("fail-message"));
                  }

                  this.teleportingPlayers.remove(playerId);
                  this.startLocations.remove(playerId);
               });
            } else {
               this.rtpManager.playSound(player, "teleport_fail");
               this.sendActionBar(player, this.rtpManager.getActionBar("fail-message"));
               player.sendMessage(this.rtpManager.getMessage("fail-message"));
               this.teleportingPlayers.remove(playerId);
               this.startLocations.remove(playerId);
            }

         }));
   }

   private void sendActionBar(Player player, String message) {
      try {
         player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
      } catch (Exception var4) {
      }

   }
}

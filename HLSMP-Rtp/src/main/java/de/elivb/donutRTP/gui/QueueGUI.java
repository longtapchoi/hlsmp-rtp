package de.elivb.donutRTP.gui;

import de.elivb.donutRTP.Hex;
import de.elivb.donutRTP.RTP;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

public class QueueGUI implements Listener {
   private final RTP plugin;
   private FileConfiguration queueConfig;
   private final Map<String, List<UUID>> queues;
   private final Map<UUID, String> playerQueue;
   private final Map<String, Object> activeTasks;
   private final Map<String, Integer> countdowns;
   private final Map<UUID, Object> glowingTasks;
   private final Map<UUID, QueueStatus> playerStatus;
   private final Map<UUID, String> pendingTeleports;
   private final Map<UUID, Object> cooldownTasks;

   public QueueGUI(RTP plugin) {
      this.plugin = plugin;
      this.queues = new ConcurrentHashMap();
      this.playerQueue = new ConcurrentHashMap();
      this.activeTasks = new ConcurrentHashMap();
      this.countdowns = new ConcurrentHashMap();
      this.glowingTasks = new ConcurrentHashMap();
      this.playerStatus = new ConcurrentHashMap();
      this.pendingTeleports = new ConcurrentHashMap();
      this.cooldownTasks = new ConcurrentHashMap();
      this.loadQueueConfig();
      plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> this.cleanupEmptyQueues(), 600L, 600L);
   }

   private void loadQueueConfig() {
      File guiFolder = new File(this.plugin.getDataFolder(), "gui");
      if (!guiFolder.exists()) {
         boolean created = guiFolder.mkdirs();
         if (!created) {
         }
      }

      File queueFile = new File(guiFolder, "queue.yml");
      if (queueFile.exists()) {
         this.queueConfig = YamlConfiguration.loadConfiguration(queueFile);
         this.initializeQueues();
      } else {
         this.createDefaultQueueConfig(queueFile);
         this.queueConfig = YamlConfiguration.loadConfiguration(queueFile);
         this.initializeQueues();
      }

   }

   private void createDefaultQueueConfig(File queueFile) {
      try {
         YamlConfiguration config = new YamlConfiguration();
         config.set("title", "&8&lHàng Đợi RTP");
         config.set("rows", 3);
         config.set("worlds.overworld.slot", 11);
         config.set("worlds.overworld.world", "world");
         config.set("worlds.overworld.material", "GRASS_BLOCK");
         config.set("worlds.overworld.name", "&#00FF89ᴛʜế ɢɪớɪ ɢốᴄ");
         config.set("worlds.overworld.lore", Arrays.asList("", "&bThông tin:", "&fThi đấu 1v1 với người chơi khác", "&fỞ các &#00FF89thế giới khác nhau.", "", "&#00FF89&l⏺ &fNgười chơi trong hàng đợi: &#00FF89%queue-amount%/2", "", "&#E0E319&l➟ &#E0E319&l&nNHẤN&#E0E319 để vào hàng đợi"));
         config.set("worlds.nether.slot", 13);
         config.set("worlds.nether.world", "world_nether");
         config.set("worlds.nether.material", "NETHERRACK");
         config.set("worlds.nether.name", "&#00FF89ɴᴇᴛʜᴇʀ");
         config.set("worlds.nether.lore", Arrays.asList("", "&bThông tin:", "&fThi đấu 1v1 với người chơi khác", "&fỞ các &#00FF89thế giới khác nhau.", "", "&#00FF89&l⏺ &fNgười chơi trong hàng đợi: &#00FF89%queue-amount%/2", "", "&#E0E319&l➟ &#E0E319&l&nNHẤN&#E0E319 để vào hàng đợi"));
         config.set("worlds.end.slot", 15);
         config.set("worlds.end.world", "world_the_end");
         config.set("worlds.end.material", "END_STONE");
         config.set("worlds.end.name", "&#00FF89ᴇɴᴅ");
         config.set("worlds.end.lore", Arrays.asList("", "&bThông tin:", "&fThi đấu 1v1 với người chơi khác", "&fỞ các &#00FF89thế giới khác nhau.", "", "&#00FF89&l⏺ &fNgười chơi trong hàng đợi: &#00FF89%queue-amount%/2", "", "&#E0E319&l➟ &#E0E319&l&nNHẤN&#E0E319 để vào hàng đợi"));
         config.set("queue-settings.players-per-match", 2);
         config.set("queue-settings.countdown-seconds", 10);
         config.set("queue-settings.max-wait-time-seconds", 120);
         config.set("queue-settings.arena-distance", 10);
         config.set("queue-settings.glowing-duration", 6);
         config.set("queue-settings.post-match-cooldown", 5);
         config.save(queueFile);
      } catch (Exception var3) {
      }

   }

   private void initializeQueues() {
      this.queues.clear();
      this.countdowns.clear();
      if (this.queueConfig != null && this.queueConfig.contains("worlds")) {
         ConfigurationSection cs = this.queueConfig.getConfigurationSection("worlds");
         if (cs != null) {
            for(String worldKey : cs.getKeys(false)) {
               String worldName = this.queueConfig.getString("worlds." + worldKey + ".world");
               if (worldName != null) {
                  this.queues.put(worldName, new ArrayList());
                  this.countdowns.put(worldName, this.queueConfig.getInt("queue-settings.countdown-seconds", 10));
               }
            }
         }
      }

   }

   public void openQueueGUI(Player player) {
      try {
         if (this.playerStatus.getOrDefault(player.getUniqueId(), QueueGUI.QueueStatus.IN_QUEUE) == QueueGUI.QueueStatus.COOLDOWN) {
            player.sendMessage(this.getMessage("messages.rtp-cooldown-message"));
            return;
         }

         if (this.playerStatus.getOrDefault(player.getUniqueId(), QueueGUI.QueueStatus.IN_QUEUE) == QueueGUI.QueueStatus.IN_MATCH) {
            player.sendMessage(this.getMessage("messages.rtp-already-in-match"));
            return;
         }

         String title = Hex.translateAllColorCodes(this.queueConfig.getString("title", "&8&lHàng Đợi RTP"));
         int rows = this.queueConfig.getInt("rows", 3);
         Inventory gui = Bukkit.createInventory((InventoryHolder)null, rows * 9, title);
         if (this.queueConfig != null && this.queueConfig.contains("worlds")) {
            ConfigurationSection cs = this.queueConfig.getConfigurationSection("worlds");
            if (cs != null) {
               for(String worldKey : cs.getKeys(false)) {
                  this.setupQueueItem(gui, worldKey);
               }
            }
         }

         player.openInventory(gui);
      } catch (Exception var8) {
      }

   }

   private void setupQueueItem(Inventory gui, String worldKey) {
      String path = "worlds." + worldKey;
      int slot = this.queueConfig.getInt(path + ".slot");
      String worldName = this.queueConfig.getString(path + ".world");
      Material material = Material.getMaterial(this.queueConfig.getString(path + ".material", "GRASS_BLOCK"));
      String itemName = Hex.translateAllColorCodes(this.queueConfig.getString(path + ".name", ""));
      if (material == null) {
         material = Material.GRASS_BLOCK;
      }

      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(itemName);
         List<String> lore = new ArrayList();

         for(String line : this.queueConfig.getStringList(path + ".lore")) {
            if (line.contains("%queue-amount%")) {
               int queueSize = this.getQueueSize(worldName);
               int requiredPlayers = this.queueConfig.getInt("queue-settings.players-per-match", 2);
               line = line.replace("%queue-amount%", String.valueOf(queueSize));
               line = line.replace("/2", "/" + requiredPlayers);
            }

            lore.add(Hex.translateAllColorCodes(line));
         }

         meta.setLore(lore);
         item.setItemMeta(meta);
         if (slot >= 0 && slot < gui.getSize()) {
            gui.setItem(slot, item);
         }

      }
   }

   private int getQueueSize(String worldName) {
      List<UUID> queue = (List)this.queues.get(worldName);
      return queue != null ? queue.size() : 0;
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player) {
         Player player = (Player)event.getWhoClicked();
         String title = ChatColor.stripColor(event.getView().getTitle());
         String expectedTitle = ChatColor.stripColor(Hex.translateAllColorCodes(this.queueConfig.getString("title", "&8&lHàng Đợi RTP")));
         if (title.equals(expectedTitle)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot >= 0 && slot < event.getInventory().getSize()) {
               String worldKey = this.getWorldKeyFromSlot(slot);
               if (worldKey != null) {
                  this.handleQueueClick(player, worldKey);
               }
            }
         }
      }
   }

   private String getWorldKeyFromSlot(int slot) {
      if (this.queueConfig != null && this.queueConfig.contains("worlds")) {
         ConfigurationSection cs = this.queueConfig.getConfigurationSection("worlds");
         if (cs == null) {
            return null;
         } else {
            for(String worldKey : cs.getKeys(false)) {
               if (this.queueConfig.getInt("worlds." + worldKey + ".slot") == slot) {
                  return worldKey;
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private void handleQueueClick(Player player, String worldKey) {
      String worldName = this.queueConfig.getString("worlds." + worldKey + ".world");
      if (worldName != null) {
         UUID playerId = player.getUniqueId();
         QueueStatus status = (QueueStatus)this.playerStatus.getOrDefault(playerId, QueueGUI.QueueStatus.IN_QUEUE);
         if (status != QueueGUI.QueueStatus.MATCH_FOUND && status != QueueGUI.QueueStatus.TELEPORTING) {
            if (status == QueueGUI.QueueStatus.COOLDOWN) {
               player.sendMessage(this.getMessage("messages.rtp-cooldown-message"));
               player.closeInventory();
            } else {
               if (this.playerQueue.containsKey(playerId)) {
                  String currentQueue = (String)this.playerQueue.get(playerId);
                  if (currentQueue.equals(worldName)) {
                     this.leaveQueue(player, worldName);
                  } else {
                     this.leaveQueue(player, currentQueue);
                     this.joinQueue(player, worldName);
                  }
               } else {
                  this.joinQueue(player, worldName);
               }

               player.closeInventory();
            }
         } else {
            player.sendMessage(this.getMessage("messages.rtp-cannot-leave-match-found"));
            player.closeInventory();
         }
      }
   }

   private void joinQueue(Player player, String worldName) {
      UUID playerId = player.getUniqueId();
      if (this.playerStatus.getOrDefault(playerId, QueueGUI.QueueStatus.IN_QUEUE) == QueueGUI.QueueStatus.COOLDOWN) {
         player.sendMessage(this.getMessage("messages.rtp-cooldown-message"));
      } else if (this.playerStatus.getOrDefault(playerId, QueueGUI.QueueStatus.IN_QUEUE) == QueueGUI.QueueStatus.IN_MATCH) {
         player.sendMessage(this.getMessage("messages.rtp-already-in-match"));
      } else {
         if (this.playerQueue.containsKey(playerId)) {
            String oldWorld = (String)this.playerQueue.get(playerId);
            this.leaveQueue(player, oldWorld);
         }

         List<UUID> queue = (List)this.queues.computeIfAbsent(worldName, (k) -> new ArrayList());
         if (!queue.contains(playerId)) {
            queue.add(playerId);
            this.playerQueue.put(playerId, worldName);
            this.playerStatus.put(playerId, QueueGUI.QueueStatus.IN_QUEUE);
            this.sendJoinMessages(player, queue.size());
            if (queue.size() == 1) {
               this.broadcastFirstPlayerJoin(player);
            }

            this.checkAndStartMatchmaking(worldName);
         }

      }
   }

   private void sendJoinMessages(Player player, int queueSize) {
      int requiredPlayers = this.queueConfig.getInt("queue-settings.players-per-match", 2);
      int playersNeeded = requiredPlayers - queueSize;
      String actionBar = this.getActionBar("rtp-queue-action-bar").replace("%players%", String.valueOf(playersNeeded));
      this.sendActionBar(player, actionBar);
      String title = this.getMessage("messages.rtp-join-queue-title");
      String subTitle = this.getMessage("messages.rtp-join-queue-sub-title");
      player.sendTitle(title, subTitle, 10, 40, 10);
      player.sendMessage(this.getMessage("messages.rtp-join-queue-message"));
      player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.5F);
   }

   private void broadcastFirstPlayerJoin(Player player) {
      for(String message : this.plugin.getLangConfig().getStringList("messages.rtp-first-join-queue-broadcast")) {
         String formattedMessage = Hex.translateAllColorCodes(message.replace("%player%", player.getName()).replace("/rtpqueue", "/rtpqueue"));
         Bukkit.broadcastMessage(formattedMessage);
      }

   }

   private void leaveQueue(Player player, String worldName) {
      UUID playerId = player.getUniqueId();
      QueueStatus status = (QueueStatus)this.playerStatus.getOrDefault(playerId, QueueGUI.QueueStatus.IN_QUEUE);
      if (status != QueueGUI.QueueStatus.MATCH_FOUND && status != QueueGUI.QueueStatus.TELEPORTING) {
         if (status == QueueGUI.QueueStatus.IN_MATCH) {
            player.sendMessage(this.getMessage("messages.rtp-cannot-leave-in-match"));
         } else {
            List<UUID> queue = (List)this.queues.get(worldName);
            if (queue != null) {
               queue.remove(playerId);
               if (queue.isEmpty() && this.activeTasks.containsKey(worldName)) {
                  Object task = this.activeTasks.remove(worldName);
                  if (task != null) {
                     this.cancelTask(task);
                  }

                  this.countdowns.remove(worldName);
               }
            }

            this.playerQueue.remove(playerId);
            this.playerStatus.remove(playerId);
            this.sendQuitMessages(player);
         }
      } else {
         player.sendMessage(this.getMessage("messages.rtp-cannot-leave-match-found"));
      }
   }

   private void cancelTask(Object task) {
      if (task instanceof ScheduledTask) {
         ((ScheduledTask)task).cancel();
      }

   }

   private void sendQuitMessages(Player player) {
      this.sendActionBar(player, this.getActionBar("rtp-quit-queue-action-bar"));
      String title = this.getMessage("messages.rtp-quit-queue-title");
      String subTitle = this.getMessage("messages.rtp-quit-queue-sub-title");
      player.sendTitle(title, subTitle, 10, 40, 10);
      player.sendMessage(this.getMessage("messages.rtp-quit-queue-message"));
      player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.5F);
   }

   private void checkAndStartMatchmaking(String worldName) {
      List<UUID> queue = (List)this.queues.get(worldName);
      if (queue != null) {
         int requiredPlayers = this.queueConfig.getInt("queue-settings.players-per-match", 2);
         if (queue.size() >= requiredPlayers && !this.activeTasks.containsKey(worldName)) {
            this.startCountdown(worldName);
         }

      }
   }

   private void startCountdown(String worldName) {
      int initialCountdown = this.queueConfig.getInt("queue-settings.countdown-seconds", 10);
      this.countdowns.put(worldName, initialCountdown);
      ScheduledTask task = this.plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(this.plugin, (scheduledTask) -> {
         List<UUID> queue = (List)this.queues.get(worldName);
         if (queue != null && queue.size() >= 2) {
            int currentCountdown = (Integer)this.countdowns.getOrDefault(worldName, initialCountdown);
            if (currentCountdown > 0) {
               for(UUID playerId : queue) {
                  Player player = Bukkit.getPlayer(playerId);
                  if (player != null && player.isOnline()) {
                     String message = this.getMessage("messages.rtp-queue-countdown-message").replace("%seconds%", String.valueOf(currentCountdown));
                     player.sendMessage(message);
                     String actionBar = this.getActionBar("rtp-queue-countdown-action-bar").replace("%seconds%", String.valueOf(currentCountdown));
                     this.sendActionBar(player, actionBar);
                     if (currentCountdown <= 3) {
                        String title = this.getMessage("messages.rtp-queue-countdown-title");
                        String subTitle = this.getMessage("messages.rtp-queue-countdown-sub-title").replace("%seconds%", String.valueOf(currentCountdown));
                        player.sendTitle(title, subTitle, 0, 20, 0);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 1.0F);
                     }
                  }
               }

               this.countdowns.put(worldName, currentCountdown - 1);
            } else {
               for(UUID playerId : queue) {
                  this.playerStatus.put(playerId, QueueGUI.QueueStatus.MATCH_FOUND);
                  this.pendingTeleports.put(playerId, worldName);
               }

               this.plugin.getServer().getGlobalRegionScheduler().execute(this.plugin, () -> this.findArenaAndStartMatch(worldName));
               this.stopCountdown(worldName);
            }
         } else {
            this.stopCountdown(worldName);
         }
      }, 1L, 20L);
      this.activeTasks.put(worldName, task);
   }

   private void stopCountdown(String worldName) {
      Object task = this.activeTasks.remove(worldName);
      if (task != null) {
         this.cancelTask(task);
      }

      this.countdowns.remove(worldName);
   }

   private void findArenaAndStartMatch(String worldName) {
      List<UUID> queue = (List)this.queues.get(worldName);
      if (queue != null && queue.size() >= 2) {
         World world = Bukkit.getWorld(worldName);
         if (world != null) {
            this.plugin.getRtpManager().findSafeLocationAsync(world).thenAccept((player1Location) -> {
               if (player1Location == null) {
                  for(UUID playerId : queue) {
                     Player player = Bukkit.getPlayer(playerId);
                     if (player != null) {
                        this.playerStatus.remove(playerId);
                        this.pendingTeleports.remove(playerId);
                     }
                  }

               } else {
                  int arenaDistance = this.queueConfig.getInt("queue-settings.arena-distance", 10);
                  Location player2Location = this.findSafeLocationNearby(world, player1Location, arenaDistance);
                  if (player2Location == null) {
                     for(UUID playerId : queue) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null) {
                           this.playerStatus.remove(playerId);
                           this.pendingTeleports.remove(playerId);
                        }
                     }

                  } else {
                     List<Player> matchPlayers = new ArrayList();

                     for(int i = 0; i < Math.min(2, queue.size()); ++i) {
                        Player player = Bukkit.getPlayer((UUID)queue.get(i));
                        if (player != null && player.isOnline()) {
                           matchPlayers.add(player);
                        }
                     }

                     if (matchPlayers.size() < 2) {
                        for(UUID playerId : queue) {
                           this.playerStatus.remove(playerId);
                           this.pendingTeleports.remove(playerId);
                        }

                     } else {
                        Location finalPlayer1Loc = player1Location;
                        Location finalPlayer2Loc = player2Location;
                        List<Player> finalMatchPlayers = new ArrayList(matchPlayers);
                        String finalWorldName = worldName;

                        for(Player player : finalMatchPlayers) {
                           player.getScheduler().run(this.plugin, (scheduledTask) -> this.teleportPlayersToArena(finalMatchPlayers, finalPlayer1Loc, finalPlayer2Loc, finalWorldName), (Runnable)null);
                        }

                     }
                  }
               }
            });
         } else {
            for(UUID playerId : queue) {
               this.playerStatus.remove(playerId);
               this.pendingTeleports.remove(playerId);
            }

         }
      }
   }

   private Location findSafeLocationNearby(World world, Location center, int distance) {
      Random random = new Random();

      for(int i = 0; i < 10; ++i) {
         int offsetX = random.nextInt(distance * 2) - distance;
         int offsetZ = random.nextInt(distance * 2) - distance;
         int x = center.getBlockX() + offsetX;
         int z = center.getBlockZ() + offsetZ;
         int y = world.getHighestBlockYAt(x, z) + 1;
         if (y > world.getMinHeight() && y < world.getMaxHeight()) {
            Location location = new Location(world, (double)x + (double)0.5F, (double)y, (double)z + (double)0.5F);
            if (this.isLocationSafe(location)) {
               return location;
            }
         }
      }

      return null;
   }

   private boolean isLocationSafe(Location location) {
      World world = location.getWorld();
      if (world == null) {
         return false;
      } else {
         int x = location.getBlockX();
         int y = location.getBlockY();
         int z = location.getBlockZ();
         Material blockBelow = world.getBlockAt(x, y - 1, z).getType();
         Material blockAtFeet = world.getBlockAt(x, y, z).getType();
         Material blockAtHead = world.getBlockAt(x, y + 1, z).getType();
         return blockBelow.isSolid() && !blockAtFeet.isSolid() && !blockAtHead.isSolid() && blockBelow != Material.LAVA && blockBelow != Material.WATER && blockAtFeet != Material.LAVA && blockAtFeet != Material.WATER && blockAtHead != Material.LAVA && blockAtHead != Material.WATER;
      }
   }

   private void teleportPlayersToArena(List<Player> players, Location loc1, Location loc2, String worldName) {
      Player player1 = (Player)players.get(0);
      Player player2 = (Player)players.get(1);

      for(Player player : players) {
         this.playerStatus.put(player.getUniqueId(), QueueGUI.QueueStatus.TELEPORTING);
         this.pendingTeleports.put(player.getUniqueId(), worldName);
      }

      loc1.setDirection(loc2.clone().subtract(loc1).toVector());
      loc2.setDirection(loc1.clone().subtract(loc2).toVector());
      player1.teleportAsync(loc1);
      player2.teleportAsync(loc2);
      List<Player> finalPlayers = new ArrayList(players);
      if (!finalPlayers.isEmpty()) {
         ((Player)finalPlayers.get(0)).getScheduler().runDelayed(this.plugin, (scheduledTask) -> {
            for(Player player : finalPlayers) {
               this.playerStatus.put(player.getUniqueId(), QueueGUI.QueueStatus.IN_MATCH);
               this.leaveQueueInternal(player, worldName);
               this.startPostMatchCooldown(player);
            }

            this.applyGlowingEffect(player1);
            this.applyGlowingEffect(player2);

            for(Player player : finalPlayers) {
               String title = this.getMessage("messages.rtp-queue-teleporting-title");
               String subTitle = this.getMessage("messages.rtp-queue-teleporting-sub-title");
               player.sendTitle(title, subTitle, 10, 40, 10);
               player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            }

         }, (Runnable)null, 2L);
      }

   }

   private void leaveQueueInternal(Player player, String worldName) {
      UUID playerId = player.getUniqueId();
      List<UUID> queue = (List)this.queues.get(worldName);
      if (queue != null) {
         queue.remove(playerId);
         if (queue.isEmpty() && this.activeTasks.containsKey(worldName)) {
            Object task = this.activeTasks.remove(worldName);
            if (task != null) {
               this.cancelTask(task);
            }

            this.countdowns.remove(worldName);
         }
      }

      this.playerQueue.remove(playerId);
      this.pendingTeleports.remove(playerId);
   }

   private void startPostMatchCooldown(Player player) {
      UUID playerId = player.getUniqueId();
      this.playerStatus.put(playerId, QueueGUI.QueueStatus.COOLDOWN);
      int cooldownSeconds = this.queueConfig.getInt("queue-settings.post-match-cooldown", 5);
      ScheduledTask task = player.getScheduler().runDelayed(this.plugin, (scheduledTask) -> {
         this.playerStatus.remove(playerId);
         this.cooldownTasks.remove(playerId);
      }, (Runnable)null, (long)cooldownSeconds * 20L);
      this.cooldownTasks.put(playerId, task);
   }

   private void applyGlowingEffect(Player player) {
      int glowingDuration = this.queueConfig.getInt("queue-settings.glowing-duration", 6);
      UUID pid = player.getUniqueId();
      Object existing = this.glowingTasks.remove(pid);
      if (existing != null) {
         this.cancelTask(existing);
      }

      if (glowingDuration <= 0) {
         if (player.isOnline()) {
            player.setGlowing(false);

            try {
               PotionEffectType glow = PotionEffectType.GLOWING;
               if (glow != null) {
                  player.removePotionEffect(glow);
               }
            } catch (Exception var6) {
            }
         }

      } else {
         player.getScheduler().run(this.plugin, (scheduledTask) -> {
            try {
               player.setGlowing(true);

               try {
                  PotionEffectType glow = PotionEffectType.GLOWING;
                  if (glow != null) {
                     player.addPotionEffect(new PotionEffect(glow, glowingDuration * 20, 0, true, false, true));
                  }
               } catch (Exception var8) {
               }

               player.getScheduler().runDelayed(this.plugin, (removeTask) -> {
                  Object t = this.glowingTasks.remove(pid);
                  if (t != null) {
                     this.cancelTask(t);
                  }

                  if (player.isOnline()) {
                     player.setGlowing(false);

                     try {
                        PotionEffectType glow = PotionEffectType.GLOWING;
                        if (glow != null) {
                           player.removePotionEffect(glow);
                        }
                     } catch (Exception var6) {
                     }
                  }

               }, (Runnable)null, (long)glowingDuration * 20L);
            } catch (Exception var9) {
               try {
                  this.applyGlowingViaScoreboard(player, glowingDuration);
               } catch (Exception var7) {
               }
            }

         }, (Runnable)null);
      }
   }

   private void applyGlowingViaScoreboard(Player player, int durationSeconds) {
      Player finalPlayer = player;

      try {
         ScoreboardManager manager = Bukkit.getScoreboardManager();
         if (manager == null) {
            return;
         }

         Scoreboard board = manager.getMainScoreboard();
         Team team = board.getTeam("RTP_Glowing");
         if (team == null) {
            team = board.registerNewTeam("RTP_Glowing");
         }

         team.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.ALWAYS);
         team.setColor(ChatColor.YELLOW);
         team.addEntry(finalPlayer.getName());
         player.getScheduler().runDelayed(this.plugin, (scheduledTask) -> {
            if (finalPlayer.isOnline()) {
               team.removeEntry(finalPlayer.getName());
            }

            if (team.getEntries().isEmpty()) {
               team.unregister();
            }

         }, (Runnable)null, (long)durationSeconds * 20L);
      } catch (Exception var8) {
      }

   }

   @EventHandler
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      UUID playerId = player.getUniqueId();
      Object gt = this.glowingTasks.remove(playerId);
      if (gt != null) {
         this.cancelTask(gt);
      }

      try {
         if (player.isOnline()) {
            player.setGlowing(false);

            try {
               PotionEffectType glow = PotionEffectType.GLOWING;
               if (glow != null) {
                  player.removePotionEffect(glow);
               }
            } catch (Exception var6) {
            }
         }
      } catch (Exception var7) {
      }

   }

   private void cleanupEmptyQueues() {
      Iterator<Map.Entry<String, List<UUID>>> iterator = this.queues.entrySet().iterator();

      while(iterator.hasNext()) {
         Map.Entry<String, List<UUID>> entry = (Map.Entry)iterator.next();
         if (((List)entry.getValue()).isEmpty()) {
            iterator.remove();
            this.countdowns.remove(entry.getKey());
            Object task = this.activeTasks.remove(entry.getKey());
            if (task != null) {
               this.cancelTask(task);
            }
         }
      }

   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      this.playerQuit(event.getPlayer());
   }

   public void playerQuit(Player player) {
      UUID playerId = player.getUniqueId();
      QueueStatus status = (QueueStatus)this.playerStatus.get(playerId);
      if (status == QueueGUI.QueueStatus.MATCH_FOUND || status == QueueGUI.QueueStatus.TELEPORTING || status == QueueGUI.QueueStatus.IN_MATCH) {
         int penaltyCooldown = 30;
         this.playerStatus.put(playerId, QueueGUI.QueueStatus.COOLDOWN);
         ScheduledTask task = player.getScheduler().runDelayed(this.plugin, (scheduledTask) -> {
            this.playerStatus.remove(playerId);
            this.cooldownTasks.remove(playerId);
         }, (Runnable)null, (long)penaltyCooldown * 20L);
         this.cooldownTasks.put(playerId, task);

         for(String worldName : this.pendingTeleports.values()) {
            List<UUID> queue = (List)this.queues.get(worldName);
            if (queue != null) {
               for(UUID otherId : queue) {
                  if (!otherId.equals(playerId)) {
                     Player otherPlayer = Bukkit.getPlayer(otherId);
                     if (otherPlayer != null) {
                        otherPlayer.sendMessage(this.getMessage("messages.rtp-opponent-left"));
                     }
                  }
               }
            }
         }
      }

      if (this.playerQueue.containsKey(playerId)) {
         String worldName = (String)this.playerQueue.get(playerId);
         this.leaveQueueInternal(player, worldName);
      }

      this.playerStatus.remove(playerId);
      this.pendingTeleports.remove(playerId);
      Object ct = this.cooldownTasks.remove(playerId);
      if (ct != null) {
         this.cancelTask(ct);
      }

      Object gt = this.glowingTasks.remove(playerId);
      if (gt != null) {
         this.cancelTask(gt);
      }

      try {
         if (player.isOnline()) {
            player.setGlowing(false);

            try {
               PotionEffectType glow = PotionEffectType.GLOWING;
               if (glow != null) {
                  player.removePotionEffect(glow);
               }
            } catch (Exception var12) {
            }
         }
      } catch (Exception var13) {
      }

   }

   public void endMatchForPlayer(Player player) {
      UUID playerId = player.getUniqueId();
      this.playerStatus.remove(playerId);
      this.pendingTeleports.remove(playerId);
      Object ct = this.cooldownTasks.remove(playerId);
      if (ct != null) {
         this.cancelTask(ct);
      }

      this.startPostMatchCooldown(player);
   }

   private void sendActionBar(Player player, String message) {
      try {
         player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(Hex.translateAllColorCodes(message)));
      } catch (Exception var4) {
      }

   }

   private String getMessage(String path) {
      String message = this.plugin.getLangConfig().getString(path, "&c" + path);
      return Hex.translateAllColorCodes(message);
   }

   private String getActionBar(String path) {
      String message = this.plugin.getLangConfig().getString("action-bars." + path, this.plugin.getLangConfig().getString("messages." + path, "&c" + path));
      return Hex.translateAllColorCodes(message);
   }

   public void reloadQueueConfig() {
      this.loadQueueConfig();
   }

   public QueueStatus getPlayerStatus(UUID playerId) {
      return (QueueStatus)this.playerStatus.getOrDefault(playerId, QueueGUI.QueueStatus.IN_QUEUE);
   }

   public boolean isPlayerInMatch(UUID playerId) {
      QueueStatus status = (QueueStatus)this.playerStatus.get(playerId);
      return status == QueueGUI.QueueStatus.IN_MATCH || status == QueueGUI.QueueStatus.MATCH_FOUND || status == QueueGUI.QueueStatus.TELEPORTING;
   }

   public static enum QueueStatus {
      IN_QUEUE,
      MATCH_FOUND,
      TELEPORTING,
      IN_MATCH,
      COOLDOWN;

      // $FF: synthetic method
      private static QueueStatus[] $values() {
         return new QueueStatus[]{IN_QUEUE, MATCH_FOUND, TELEPORTING, IN_MATCH, COOLDOWN};
      }
   }
}

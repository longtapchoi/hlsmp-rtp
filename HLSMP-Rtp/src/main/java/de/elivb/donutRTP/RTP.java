package de.elivb.donutRTP;

import de.elivb.donutRTP.Manager.RTPManager;
import de.elivb.donutRTP.gui.QueueGUI;
import de.elivb.donutRTP.gui.RTPGui;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class RTP extends JavaPlugin implements TabCompleter {
   private RTPManager rtpManager;
   private RTPGui rtpGui;
   private QueueGUI queueGUI;
   private FileConfiguration langConfig;
   private final ConcurrentHashMap<Integer, ScheduledTask> runningTasks = new ConcurrentHashMap();
   private final AtomicInteger taskCounter = new AtomicInteger(0);
   private LicenseManager licenseManager;

   public void onEnable() {
      this.licenseManager = new LicenseManager(this);
      if (this.licenseManager.validateLicenseOnStartup()) {
         if (!this.getDataFolder().exists()) {
            boolean created = this.getDataFolder().mkdirs();
            if (!created) {
            }
         }

         this.saveDefaultConfig();
         this.saveDefaultLangConfig();
         File guiFolder = new File(this.getDataFolder(), "gui");
         if (!guiFolder.exists()) {
            boolean created = guiFolder.mkdirs();
            if (!created) {
            }
         }

         this.rtpManager = new RTPManager(this);
         this.rtpGui = new RTPGui(this, this.rtpManager);
         this.queueGUI = new QueueGUI(this);
         this.getServer().getPluginManager().registerEvents(this.rtpGui, this);
         this.getServer().getPluginManager().registerEvents(this.queueGUI, this);
         this.getCommand("rtp").setExecutor(this);
         this.getCommand("rtp").setTabCompleter(this);
         PluginCommand queueCommand = this.getCommand("rtpqueue");
         if (queueCommand != null) {
            queueCommand.setExecutor((sender, command, label, args) -> {
               if (!(sender instanceof Player player)) {
                  sender.sendMessage(this.rtpManager.getMessage("player-only"));
                  return true;
               } else if (!player.hasPermission("rtp.use")) {
                  player.sendMessage(this.rtpManager.getMessage("messages.no-permission"));
                  return true;
               } else {
                  this.queueGUI.openQueueGUI(player);
                  return true;
               }
            });
         }

      }
   }

   public LicenseManager getLicenseManager() {
      return this.licenseManager;
   }

   private void saveDefaultLangConfig() {
      File langFile = new File(this.getDataFolder(), "lang.yml");
      if (!langFile.exists()) {
         this.saveResource("lang.yml", false);
      }

      this.reloadLangConfig();
   }

   public void reloadLangConfig() {
      File langFile = new File(this.getDataFolder(), "lang.yml");
      this.langConfig = YamlConfiguration.loadConfiguration(langFile);
   }

   public FileConfiguration getLangConfig() {
      return this.langConfig;
   }

   public void runAtPlayer(Player player, Runnable task) {
      if (player.isOnline()) {
         player.getScheduler().run(this, (scheduledTask) -> task.run(), (Runnable)null);
      }

   }

   public Object runGlobalTimer(Runnable task, long delay, long period) {
      int taskId = this.taskCounter.incrementAndGet();
      ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, (scheduledTask1) -> task.run(), Math.max(1L, delay), period);
      this.runningTasks.put(taskId, scheduledTask);
      return taskId;
   }

   public void cancelTask(Object task) {
      if (task != null) {
         if (task instanceof Integer) {
            Integer taskId = (Integer)task;
            ScheduledTask scheduledTask = (ScheduledTask)this.runningTasks.remove(taskId);
            if (scheduledTask != null) {
               scheduledTask.cancel();
            }
         } else if (task instanceof ScheduledTask) {
            ((ScheduledTask)task).cancel();
         }

      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (command.getName().equalsIgnoreCase("rtp")) {
         if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("rtp.admin")) {
               sender.sendMessage(this.rtpManager.getMessage("messages.no-permission"));
               return true;
            } else {
               this.reloadConfig();
               this.reloadLangConfig();
               this.rtpGui.reloadGuiConfig();
               this.queueGUI.reloadQueueConfig();
               this.rtpManager.reloadConfig();
               if (sender instanceof Player) {
                  this.rtpManager.playSound((Player)sender, "reload");
               }

               sender.sendMessage(this.rtpManager.getActionBar("reload"));
               return true;
            }
         } else if (!(sender instanceof Player)) {
            sender.sendMessage(this.rtpManager.getMessage("messages.player-only"));
            return true;
         } else {
            Player player = (Player)sender;
            if (!player.hasPermission("rtp.use")) {
               sender.sendMessage(this.rtpManager.getMessage("messages.no-permission"));
               return true;
            } else {
               this.rtpGui.openRTPGui(player);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      List<String> completions = new ArrayList();
      if (command.getName().equalsIgnoreCase("rtp") && args.length == 1 && sender.hasPermission("rtp.admin") && "reload".startsWith(args[0].toLowerCase())) {
         completions.add("reload");
      }

      return completions;
   }

   public void onDisable() {
      for(ScheduledTask task : this.runningTasks.values()) {
         if (task != null) {
            task.cancel();
         }
      }

      this.runningTasks.clear();
      if (this.queueGUI != null) {
         for(Player player : this.getServer().getOnlinePlayers()) {
            this.queueGUI.playerQuit(player);
         }
      }

   }

   public RTPManager getRtpManager() {
      return this.rtpManager;
   }

   public RTPGui getRtpGui() {
      return this.rtpGui;
   }

   public QueueGUI getQueueGUI() {
      return this.queueGUI;
   }
}

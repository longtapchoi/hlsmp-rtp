package de.elivb.donutRTP;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.UUID;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class LicenseManager {
   private final JavaPlugin plugin;
   private final String API_URL = "http://154.43.52.66:25010/api/validate";
   private final String PLUGIN_NAME = "DonutRTP";
   private final String PLUGIN_ID = "DonutRTP";
   private File licenseFile;
   private FileConfiguration licenseConfig;
   private String serverId;
   private String licenseKey;
   private boolean licenseValid = false;

   public LicenseManager(JavaPlugin plugin) {
      this.plugin = plugin;
      this.setupLicenseFile();
      this.loadServerId();
   }

   private void setupLicenseFile() {
      this.licenseFile = new File(this.plugin.getDataFolder(), "license.yml");
      if (!this.licenseFile.exists()) {
         this.plugin.saveResource("license.yml", false);
      }

      this.licenseConfig = YamlConfiguration.loadConfiguration(this.licenseFile);
   }

   private void loadServerId() {
      if (this.licenseConfig.contains("server-id") && !this.licenseConfig.getString("server-id").isEmpty()) {
         this.serverId = this.licenseConfig.getString("server-id");
      } else {
         String serverIp = this.plugin.getServer().getIp();
         String serverPort = String.valueOf(this.plugin.getServer().getPort());
         String serverMotd = this.plugin.getServer().getMotd();
         if (serverIp.isEmpty() || serverIp.equals("0.0.0.0")) {
            serverIp = "localhost";
         }

         String combined = serverIp + ":" + serverPort + ":" + serverMotd.hashCode() + ":" + System.getProperty("user.name", "unknown") + ":" + System.getProperty("user.home", "unknown");
         this.serverId = this.generateHash(combined);
         this.licenseConfig.set("server-id", this.serverId);
         this.saveLicenseFile();
      }

   }

   public boolean validateLicenseOnStartup() {
      // [HLSMP] Bypass: license check disabled, plugin always treated as licensed.
      this.licenseKey = this.licenseConfig.getString("license-key", "");
      this.licenseValid = true;
      return true;
   }

   private boolean validateWithAPI(String licenseKey, String serverId) {
      // [HLSMP] Bypass: no remote validation, always valid.
      return true;
   }

   private void disableSSLVerification() {
      try {
         TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
               return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
         }};
         SSLContext sc = SSLContext.getInstance("SSL");
         sc.init((KeyManager[])null, trustAllCerts, new SecureRandom());
         HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
         HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
               return true;
            }
         };
         HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
      } catch (Exception var4) {
      }

   }

   private String generateHash(String input) {
      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         byte[] hash = md.digest(input.getBytes("UTF-8"));
         StringBuilder hexString = new StringBuilder();

         for(byte b : hash) {
            String hex = Integer.toHexString(255 & b);
            if (hex.length() == 1) {
               hexString.append('0');
            }

            hexString.append(hex);
         }

         String fullHash = hexString.toString().toUpperCase();
         String formatted = "";

         for(int i = 0; i < 32 && i < fullHash.length(); i += 8) {
            if (i > 0) {
               formatted = formatted + "-";
            }

            int end = Math.min(i + 8, fullHash.length());
            formatted = formatted + fullHash.substring(i, end);
         }

         return formatted;
      } catch (Exception var10) {
         return UUID.randomUUID().toString().toUpperCase().replace("-", "").substring(0, 32);
      }
   }

   private void saveLicenseFile() {
      try {
         this.licenseConfig.save(this.licenseFile);
      } catch (IOException var2) {
      }

   }

   public String getServerId() {
      return this.serverId;
   }

   public String getLicenseKey() {
      return this.licenseKey;
   }

   public boolean isLicenseValid() {
      return this.licenseValid;
   }

   public void setLicenseKey(String key) {
      this.licenseConfig.set("license-key", key);
      this.saveLicenseFile();
      this.licenseKey = key;
      this.licenseValid = this.validateWithAPI(key, this.serverId);
   }
}

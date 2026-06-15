package de.elivb.donutRTP;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;

public class Hex {
   private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

   public static String translateAllColorCodes(String message) {
      if (message == null) {
         return null;
      } else {
         Matcher matcher = HEX_PATTERN.matcher(message);
         StringBuffer buffer = new StringBuffer();

         while(matcher.find()) {
            String hexColor = matcher.group(1);
            char var10002 = hexColor.charAt(0);
            matcher.appendReplacement(buffer, "§x§" + var10002 + "§" + hexColor.charAt(1) + "§" + hexColor.charAt(2) + "§" + hexColor.charAt(3) + "§" + hexColor.charAt(4) + "§" + hexColor.charAt(5));
         }

         matcher.appendTail(buffer);
         return ChatColor.translateAlternateColorCodes('&', buffer.toString());
      }
   }

   public static String stripColorCodes(String message) {
      return message == null ? null : message.replaceAll("(&|§)[0-9a-fk-or]", "").replaceAll("&#[A-Fa-f0-9]{6}", "");
   }
}

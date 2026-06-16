package net.borisshoes.borislib.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for working with Minecraft text {@link Component}s, chat formatting, and string manipulation.
 *
 * <p>Provides helpers for:</p>
 * <ul>
 *    <li>Converting RGB colors to the closest {@link TextColor} constant.</li>
 *    <li>Generating energy bars / progress indicators via Unicode block characters.</li>
 *    <li>Parsing custom string markup into formatted {@link Component} trees.</li>
 *    <li>Converting components back into parseable markup or Java code.</li>
 *    <li>Number formatting (roman numerals, comma separators, abbreviated large values).</li>
 * </ul>
 */
public class TextUtils {
   
   /**
    * Returns a human-friendly styled {@link Component} for a dimension key (e.g. "Overworld", "The Nether", "The End").
    *
    * @param worldKey the dimension key
    * @return a formatted component representing the dimension name
    */
   public static MutableComponent getFormattedDimName(ResourceKey<Level> worldKey){
      if(worldKey.identifier().toString().equals(ServerLevel.OVERWORLD.identifier().toString())){
         return Component.literal("Overworld").withColor(TextColor.GREEN);
      }else if(worldKey.identifier().toString().equals(ServerLevel.NETHER.identifier().toString())){
         return Component.literal("The Nether").withColor(TextColor.RED);
      }else if(worldKey.identifier().toString().equals(ServerLevel.END.identifier().toString())){
         return Component.literal("The End").withColor(TextColor.DARK_PURPLE);
      }else{
         return Component.literal(worldKey.identifier().toString()).withColor(TextColor.YELLOW);
      }
   }
   
   /**
    * Sends an action-bar progress/energy bar to the given player using Unicode characters. Defaults to 10 segments.
    *
    * @param player   the player receiving the bar
    * @param percentage the fill percentage (0.0 to 1.0)
    * @param prefix   component to display before the bar
    * @param suffix   component to display after the bar
    * @param barStyle style operator applied to each bar character
    */
   public static void energyBar(ServerPlayer player, double percentage, Component prefix, Component suffix, UnaryOperator<Style> barStyle){
      TextUtils.energyBar(player, percentage, 10, prefix, suffix, barStyle);
   }
   
   /**
    * Sends an action-bar progress/energy bar to the given player using Unicode characters, with a configurable
    * number of segments.
    *
    * @param player   the player receiving the bar
    * @param percentage the fill percentage (0.0 to 1.0)
    * @param numBars  the number of bar segments to render
    * @param prefix   component to display before the bar
    * @param suffix   component to display after the bar
    * @param barStyle style operator applied to each bar character
    */
   public static void energyBar(ServerPlayer player, double percentage, int numBars, Component prefix, Component suffix, UnaryOperator<Style> barStyle){
      MutableComponent text = Component.literal("").append(prefix);
      int value = (int) (percentage * 100);
      char[] unicodeChars = {'▁', '▂', '▃', '▅', '▆', '▇', '▌'};
      for(int i = 0; i < numBars; i++){
         int segmentValue = value - (i * numBars);
         if(segmentValue <= 0){
            text.append(Component.literal(String.valueOf(unicodeChars[0])).withStyle(barStyle));
         }else if(segmentValue >= numBars){
            text.append(Component.literal(String.valueOf(unicodeChars[unicodeChars.length - 1])).withStyle(barStyle));
         }else{
            int charIndex = (int) ((double) segmentValue / numBars * (unicodeChars.length - 1));
            text.append(Component.literal(String.valueOf(unicodeChars[charIndex])).withStyle(barStyle));
         }
      }
      text.append(suffix);
      player.sendSystemMessage(text, true);
   }
   
   /**
    * Converts a camelCase string to snake_case.
    *
    * @param str the input string
    * @return the snake_case version
    */
   public static String camelToSnake(String str){
      return str.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase(Locale.ROOT);
   }
   
   /**
    * Lookup table mapping {@link TextColor} colors to their RGB integer values. Used by
    * {@link #getClosestFormatting(int)} to find the nearest named color.
    */
   public static final ArrayList<Pair<TextColor, Integer>> COLOR_MAP = new ArrayList<>(Arrays.asList(
         Pair.of(TextColor.BLACK, 0x000000),
         Pair.of(TextColor.DARK_BLUE, 0x0000AA),
         Pair.of(TextColor.DARK_GREEN, 0x00AA00),
         Pair.of(TextColor.DARK_AQUA, 0x00AAAA),
         Pair.of(TextColor.DARK_RED, 0xAA0000),
         Pair.of(TextColor.DARK_PURPLE, 0xAA00AA),
         Pair.of(TextColor.GOLD, 0xFFAA00),
         Pair.of(TextColor.GRAY, 0xAAAAAA),
         Pair.of(TextColor.DARK_GRAY, 0x555555),
         Pair.of(TextColor.BLUE, 0x5555FF),
         Pair.of(TextColor.GREEN, 0x55FF55),
         Pair.of(TextColor.AQUA, 0x55FFFF),
         Pair.of(TextColor.RED, 0xFF5555),
         Pair.of(TextColor.LIGHT_PURPLE, 0xFF55FF),
         Pair.of(TextColor.YELLOW, 0xFFFF55),
          Pair.of(TextColor.WHITE, 0xFFFFFF)
   ));
   
   /**
    * Finds the closest {@link TextColor} color constant to the given RGB value using weighted Euclidean
    * distance (R: 0.30, G: 0.59, B: 0.11).
    *
    * @param colorRGB the input color as a 24-bit RGB integer (0xRRGGBB)
    * @return the nearest formatting constant
    */
   public static TextColor getClosestFormatting(int colorRGB){
      TextColor closest = TextColor.WHITE;
      double cDist = Integer.MAX_VALUE;
      for(Pair<TextColor, Integer> pair : COLOR_MAP){
         int repColor = pair.getSecond();
         double rDist = (((repColor >> 16) & 0xFF) - ((colorRGB >> 16) & 0xFF)) * 0.30;
         double gDist = (((repColor >> 8) & 0xFF) - ((colorRGB >> 8) & 0xFF)) * 0.59;
         double bDist = ((repColor & 0xFF) - (colorRGB & 0xFF)) * 0.11;
         double dist = rDist * rDist + gDist * gDist + bDist * bDist;
         if(dist < cDist){
            cDist = dist;
            closest = pair.getFirst();
         }
      }
      return closest;
   }
   
   /**
    * Converts an integer to its Roman numeral representation (e.g. 4 → "IV", 1994 → "MCMXCIV").
    *
    * @param num the integer to convert (typically 1–3999)
    * @return the Roman numeral string
    */
   public static String intToRoman(int num){
      int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
      String[] romanLetters = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
      StringBuilder roman = new StringBuilder();
      for(int i = 0; i < values.length; i++){
         while(num >= values[i]){
            num = num - values[i];
            roman.append(romanLetters[i]);
         }
      }
      return roman.toString();
   }
   
   /**
    * Formats a large long value with abbreviated K/M/B suffixes for readability (e.g. 1,234,567 → "1.2M").
    *
    * @param value the value to format
    * @return a human-readable abbreviated string
    */
   public static String readableLong(long value){
      if(value >= 1_000_000_000) return String.format("%.2fB", value / 1_000_000_000.0);
      if(value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
      if(value >= 1_000) return String.format("%.1fK", value / 1_000.0);
      return String.valueOf(value);
   }
   
   /**
    * Formats an integer with comma separators for readability (e.g. 1234567 → "1,234,567").
    *
    * @param num the integer to format
    * @return a comma-separated string
    */
   public static String readableInt(int num){
      return String.format("%,d", num);
   }
   
   /**
    * Formats a double with comma separators and 2 decimal places.
    *
    * @param num the double to format
    * @return a formatted string
    */
   public static String readableDouble(double num){
      return readableDouble(num, 2);
   }
   
   /**
    * Formats a double with comma separators and a configurable number of decimal places.
    *
    * @param num           the double to format
    * @param decimalPlaces the number of decimal places to display
    * @return a formatted string
    */
   public static String readableDouble(double num, int decimalPlaces){
      return String.format("%,0" + (decimalPlaces + 1) + "." + decimalPlaces + "f", num);
   }
   
   /**
    * Returns a copy of the given {@link Component} with italic styling disabled.
    *
    * @param text the input component
    * @return a new component without italic
    */
   public static MutableComponent removeItalics(Component text){
      return removeItalics(Component.literal("").append(text));
   }
   
   /**
    * Returns a copy of the given {@link MutableComponent} with italic styling disabled.
    *
    * @param text the input mutable component
    * @return the same component (mutated) without italic
    */
   public static MutableComponent removeItalics(MutableComponent text){
      Style parentStyle = Style.EMPTY.withColor(TextColor.DARK_PURPLE).withItalic(false).withBold(false).withUnderlined(false).withObfuscated(false).withStrikethrough(false);
      return text.setStyle(text.getStyle().applyTo(parentStyle));
   }
   
   /**
    * Parses a custom markup string into a styled {@link Component}. Format: {@code [content](color_code)}
    * where color_code is a Minecraft formatting code (0-9,a-f) optionally followed by style flags (k,l,m,n,o).
    * <p>
    * Example: {@code "[Hello](a) [World](clo)"} → cyan "Hello" followed by red, bold, italic "World".
    *
    * @param input the markup string
    * @return the parsed component
    */
   public static MutableComponent parseString(String input){
      ArrayList<String> matchList = new ArrayList<>();
      MutableComponent text = Component.literal("");
      Pattern pattern = Pattern.compile("\\[(.*?)\\]\\(([1234567890abcdef]?[klmno]*)\\)");
      Matcher matcher = pattern.matcher(input);
      int lastEnd = 0;
      
      while(matcher.find()){
         if(!input.substring(lastEnd, matcher.start()).isEmpty())
            matchList.add("[" + input.substring(lastEnd, matcher.start()) + "](f)");
         matchList.add(matcher.group());
         lastEnd = matcher.end();
      }
      // Add the remaining part of the string
      if(!input.substring(lastEnd).isEmpty())
         matchList.add("[" + input.substring(lastEnd) + "](f)");
      
      for(String str : matchList){
         matcher = pattern.matcher(str);
         
         // Find and print all matches
         while(matcher.find()){
            String content = matcher.group(1);
            String formatCode = matcher.group(2);
            
            text.append(Component.literal(content).withStyle(parseFormatCode(formatCode)));
         }
      }
      
      return text;
   }
   
   private static ChatFormatting[] parseFormatCode(String code){
      ArrayList<ChatFormatting> formatting = new ArrayList<>();
      
      for(int i = 0; i < code.length(); i++){
         char style = code.charAt(i);
         
         ChatFormatting f = ChatFormatting.getByCode(style);
         if(f != null){
            formatting.add(f);
         }
      }
      
      return formatting.toArray(new ChatFormatting[0]);
   }
   
   /**
    * Converts a {@link Component} back into the custom markup format used by {@link #parseString}.
    *
    * @param text the component to convert
    * @return the markup string representation
    */
   public static String textToString(Component text){
      StringBuilder str = new StringBuilder();
      Style parentStyle = text.getStyle();
      
      char parentColor = 'f';
      boolean parentItalic = parentStyle.isItalic();
      boolean parentBold = parentStyle.isBold();
      boolean parentUnderlined = parentStyle.isUnderlined();
      boolean parentStrikethrough = parentStyle.isStrikethrough();
      boolean parentObfuscated = parentStyle.isObfuscated();
      
      TextColor parentTextColor = parentStyle.getColor();
      if(parentTextColor != null){
         for(ChatFormatting value : ChatFormatting.values()){
            TextColor c = TextColor.fromLegacyFormat(value);
            if(c != null && parentTextColor.getValue() == c.getValue()){
               parentColor = value.toString().charAt(1);
               break;
            }
         }
      }
      
      ComponentContents parentContent = text.getContents();
      if(parentContent instanceof PlainTextContents plainTextContent){
         String contentString = plainTextContent.text();
         
         if(!contentString.isEmpty()){
            String formatCodes = booleansToFormatCodes(parentItalic, parentBold, parentUnderlined, parentStrikethrough, parentObfuscated);
            str.append("[").append(contentString).append("](").append(parentColor).append(formatCodes).append(")");
         }
      }
      
      for(Component sibling : text.getSiblings()){
         ComponentContents siblingContent = sibling.getContents();
         
         if(siblingContent instanceof PlainTextContents plainTextContent){
            String contentString = plainTextContent.text();
            
            if(!contentString.isEmpty()){
               Style siblingStyle = sibling.getStyle();
               
               char color = parentColor;
               TextColor siblingColor = siblingStyle.getColor();
               if(siblingColor != null){
                  for(ChatFormatting value : ChatFormatting.values()){
                     TextColor c = TextColor.fromLegacyFormat(value);
                     if(c != null && siblingColor.getValue() == c.getValue()){
                        color = value.toString().charAt(1);
                        break;
                     }
                  }
               }
               String formatCodes = booleansToFormatCodes(
                     siblingStyle.isItalic() || parentItalic,
                     siblingStyle.isBold() || parentBold,
                     siblingStyle.isUnderlined() || parentUnderlined,
                     siblingStyle.isStrikethrough() || parentStrikethrough,
                     siblingStyle.isObfuscated() || parentObfuscated);
               
               str.append("[").append(contentString).append("](").append(color).append(formatCodes).append(")");
            }
         }
      }
      
      return str.toString();
   }
   
   /**
    * Converts a {@link Component} into executable Java code that constructs that component.
    * Useful for generating code snippets from in-game text.
    *
    * @param text the component to convert
    * @return Java code as a string
    */
   public static String textToCode(Component text){
      Style parentStyle = text.getStyle();
      ArrayList<String> codes = new ArrayList<>();
      
      ChatFormatting parentColor = ChatFormatting.WHITE;
      boolean parentItalic = parentStyle.isItalic();
      boolean parentBold = parentStyle.isBold();
      boolean parentUnderlined = parentStyle.isUnderlined();
      boolean parentStrikethrough = parentStyle.isStrikethrough();
      boolean parentObfuscated = parentStyle.isObfuscated();
      
      TextColor parentTextColor = parentStyle.getColor();
      if(parentTextColor != null){
         for(ChatFormatting value : ChatFormatting.values()){
            TextColor c = TextColor.fromLegacyFormat(value);
            if(c != null && parentTextColor.getValue() == c.getValue()){
               parentColor = value;
               break;
            }
         }
      }
      
      ComponentContents parentContent = text.getContents();
      if(parentContent instanceof PlainTextContents plainTextContent){
         String contentString = plainTextContent.text();
         
         if(!contentString.isEmpty()){
            codes.add(textToCodeHelper(contentString, TextColor.fromLegacyFormat(parentColor).serialize(), parentItalic, parentBold, parentUnderlined, parentStrikethrough, parentObfuscated));
         }
      }
      
      for(Component sibling : text.getSiblings()){
         ComponentContents siblingContent = sibling.getContents();
         
         if(siblingContent instanceof PlainTextContents plainTextContent){
            String contentString = plainTextContent.text();
            
            if(!contentString.isEmpty()){
               Style siblingStyle = sibling.getStyle();
               
               ChatFormatting color = parentColor;
               TextColor siblingColor = siblingStyle.getColor();
               if(siblingColor != null){
                  for(ChatFormatting value : ChatFormatting.values()){
                     TextColor c = TextColor.fromLegacyFormat(value);
                     if(c != null && siblingColor.getValue() == c.getValue()){
                        color = value;
                        break;
                     }
                  }
               }
               
               codes.add(textToCodeHelper(contentString, TextColor.fromLegacyFormat(color).serialize(),
                     siblingStyle.isItalic() || parentItalic,
                     siblingStyle.isBold() || parentBold,
                     siblingStyle.isUnderlined() || parentUnderlined,
                     siblingStyle.isStrikethrough() || parentStrikethrough,
                     siblingStyle.isObfuscated() || parentObfuscated));
            }
         }
      }
      
      if(codes.isEmpty()){
         return "Component.literal(\"\");";
      }else if(codes.size() == 1){
         return codes.getFirst();
      }else{
         String finalCode = "Component.literal(\"\")";
         for(String code : codes){
            finalCode += "\n\t.append(" + code.replace(";", "") + ")";
         }
         return finalCode + ";";
      }
   }
   
   private static String textToCodeHelper(String content, String color, boolean italic, boolean bold, boolean underlined, boolean strikethrough, boolean obfuscated){
      String code = "Component.literal(\"" + content + "\")";
      
      code += ".withStyle(";
      code += "ChatFormatting." + color.toUpperCase(Locale.ROOT) + ",";
      if(italic){
         code += "ChatFormatting.ITALIC,";
      }
      if(bold){
         code += "ChatFormatting.BOLD,";
      }
      if(underlined){
         code += "ChatFormatting.UNDERLINE,";
      }
      if(strikethrough){
         code += "ChatFormatting.STRIKETHROUGH,";
      }
      if(obfuscated){
         code += "ChatFormatting.OBFUSCATED,";
      }
      code = code.substring(0, code.length() - 1) + ")";
      code += ";";
      return code;
   }
   
   private static String booleansToFormatCodes(boolean italic, boolean bold, boolean underlined, boolean strikethrough, boolean obfuscated){
      String str = "";
      if(italic){
         str += 'o';
      }
      if(bold){
         str += 'l';
      }
      if(underlined){
         str += 'n';
      }
      if(strikethrough){
         str += 'm';
      }
      if(obfuscated){
         str += 'k';
      }
      
      return str;
   }
}

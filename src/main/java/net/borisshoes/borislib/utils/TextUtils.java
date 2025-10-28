package net.borisshoes.borislib.utils;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtils {
   
   public static MutableText getFormattedDimName(RegistryKey<World> worldKey){
      if(worldKey.getValue().toString().equals(ServerWorld.OVERWORLD.getValue().toString())){
         return Text.literal("Overworld").formatted(Formatting.GREEN);
      }else if(worldKey.getValue().toString().equals(ServerWorld.NETHER.getValue().toString())){
         return Text.literal("The Nether").formatted(Formatting.RED);
      }else if(worldKey.getValue().toString().equals(ServerWorld.END.getValue().toString())){
         return Text.literal("The End").formatted(Formatting.DARK_PURPLE);
      }else{
         return Text.literal(worldKey.getValue().toString()).formatted(Formatting.YELLOW);
      }
   }
   
   public static void energyBar(ServerPlayerEntity player, double percentage, Text prefix, Text suffix, UnaryOperator<Style> barStyle){
      TextUtils.energyBar(player, percentage, 10, prefix, suffix, barStyle);
   }
   
   public static void energyBar(ServerPlayerEntity player, double percentage, int numBars, Text prefix, Text suffix, UnaryOperator<Style> barStyle){
      MutableText text = Text.literal("").append(prefix);
      int value = (int) (percentage * 100);
      char[] unicodeChars = {'▁', '▂', '▃', '▅', '▆', '▇', '▌'};
      for (int i = 0; i < numBars; i++) {
         int segmentValue = value - (i * numBars);
         if (segmentValue <= 0) {
            text.append(Text.literal(String.valueOf(unicodeChars[0])).styled(barStyle));
         } else if (segmentValue >= numBars) {
            text.append(Text.literal(String.valueOf(unicodeChars[unicodeChars.length - 1])).styled(barStyle));
         } else {
            int charIndex = (int) ((double) segmentValue / numBars * (unicodeChars.length - 1));
            text.append(Text.literal(String.valueOf(unicodeChars[charIndex])).styled(barStyle));
         }
      }
      text.append(suffix);
      player.sendMessage(text,true);
   }
   
   public static String camelToSnake(String str){
      return str.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase(Locale.ROOT);
   }
   
   public static final ArrayList<Pair<Formatting,Integer>> COLOR_MAP = new ArrayList<>(Arrays.asList(
         new Pair<>(Formatting.BLACK,0x000000),
         new Pair<>(Formatting.DARK_BLUE,0x0000AA),
         new Pair<>(Formatting.DARK_GREEN,0x00AA00),
         new Pair<>(Formatting.DARK_AQUA,0x00AAAA),
         new Pair<>(Formatting.DARK_RED,0xAA0000),
         new Pair<>(Formatting.DARK_PURPLE,0xAA00AA),
         new Pair<>(Formatting.GOLD,0xFFAA00),
         new Pair<>(Formatting.GRAY,0xAAAAAA),
         new Pair<>(Formatting.DARK_GRAY,0x555555),
         new Pair<>(Formatting.BLUE,0x5555FF),
         new Pair<>(Formatting.GREEN,0x55FF55),
         new Pair<>(Formatting.AQUA,0x55FFFF),
         new Pair<>(Formatting.RED,0xFF5555),
         new Pair<>(Formatting.LIGHT_PURPLE,0xFF55FF),
         new Pair<>(Formatting.YELLOW,0xFFFF55),
         new Pair<>(Formatting.WHITE,0xFFFFFF)
   ));
   
   public static Formatting getClosestFormatting(int colorRGB){
      Formatting closest = Formatting.WHITE;
      double cDist = Integer.MAX_VALUE;
      for(Pair<Formatting, Integer> pair : COLOR_MAP){
         int repColor = pair.getRight();
         double rDist = (((repColor>>16)&0xFF)-((colorRGB>>16)&0xFF))*0.30;
         double gDist = (((repColor>>8)&0xFF)-((colorRGB>>8)&0xFF))*0.59;
         double bDist = ((repColor&0xFF)-(colorRGB&0xFF))*0.11;
         double dist = rDist*rDist + gDist*gDist + bDist*bDist;
         if(dist < cDist){
            cDist = dist;
            closest = pair.getLeft();
         }
      }
      return closest;
   }
   
   public static String intToRoman(int num){
      int[] values = {1000,900,500,400,100,90,50,40,10,9,5,4,1};
      String[] romanLetters = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
      StringBuilder roman = new StringBuilder();
      for(int i=0;i<values.length;i++)
      {
         while(num >= values[i])
         {
            num = num - values[i];
            roman.append(romanLetters[i]);
         }
      }
      return roman.toString();
   }
   
   public static String readableInt(int num){
      return String.format("%,d", num);
   }
   
   public static String readableDouble(double num){
      return readableDouble(num,2);
   }
   
   public static String readableDouble(double num, int decimalPlaces){
      return String.format("%,0"+(decimalPlaces+1)+"."+decimalPlaces+"f", num);
   }
   
   public static MutableText removeItalics(Text text){
      return removeItalics(Text.literal("").append(text));
   }
   
   public static MutableText removeItalics(MutableText text){
      Style parentStyle = Style.EMPTY.withColor(Formatting.DARK_PURPLE).withItalic(false).withBold(false).withUnderline(false).withObfuscated(false).withStrikethrough(false);
      return text.setStyle(text.getStyle().withParent(parentStyle));
   }
   
   public static MutableText parseString(String input){
      ArrayList<String> matchList = new ArrayList<>();
      MutableText text = Text.literal("");
      Pattern pattern = Pattern.compile("\\[(.*?)\\]\\(([1234567890abcdef]?[klmno]*)\\)");
      Matcher matcher = pattern.matcher(input);
      int lastEnd = 0;
      
      while (matcher.find()){
         if(!input.substring(lastEnd, matcher.start()).isEmpty())
            matchList.add("["+input.substring(lastEnd,matcher.start())+"](f)");
         matchList.add(matcher.group());
         lastEnd = matcher.end();
      }
      // Add the remaining part of the string
      if(!input.substring(lastEnd).isEmpty())
         matchList.add("["+input.substring(lastEnd)+"](f)");
      
      for (String str : matchList){
         matcher = pattern.matcher(str);
         
         // Find and print all matches
         while (matcher.find()){
            String content = matcher.group(1);
            String formatCode = matcher.group(2);
            
            text.append(Text.literal(content).formatted(parseFormatCode(formatCode)));
         }
      }
      
      return text;
   }
   
   private static Formatting[] parseFormatCode(String code){
      ArrayList<Formatting> formatting = new ArrayList<>();
      
      for(int i = 0; i < code.length(); i++){
         char style = code.charAt(i);
         
         Formatting f = Formatting.byCode(style);
         if(f != null){
            formatting.add(f);
         }
      }
      
      return formatting.toArray(new Formatting[0]);
   }
   
   public static String textToString(Text text){
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
         Formatting formatting = Formatting.byName(parentTextColor.getName());
         if(formatting != null){
            parentColor = formatting.getCode();
         }
      }
      
      TextContent parentContent = text.getContent();
      if(parentContent instanceof PlainTextContent plainTextContent){
         String contentString = plainTextContent.string();
         
         if(!contentString.isEmpty()){
            String formatCodes = booleansToFormatCodes(parentItalic, parentBold, parentUnderlined, parentStrikethrough, parentObfuscated);
            str.append("[").append(contentString).append("](").append(parentColor).append(formatCodes).append(")");
         }
      }
      
      for(Text sibling : text.getSiblings()){
         TextContent siblingContent = sibling.getContent();
         
         if(siblingContent instanceof PlainTextContent plainTextContent){
            String contentString = plainTextContent.string();
            
            if(!contentString.isEmpty()){
               Style siblingStyle = sibling.getStyle();
               
               char color = parentColor;
               TextColor siblingColor = siblingStyle.getColor();
               if(siblingColor != null){
                  Formatting formatting = Formatting.byName(siblingColor.getName());
                  if(formatting != null){
                     color = formatting.getCode();
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
   
   public static String textToCode(Text text){
      Style parentStyle = text.getStyle();
      ArrayList<String> codes = new ArrayList<>();
      
      Formatting parentColor = Formatting.WHITE;
      boolean parentItalic = parentStyle.isItalic();
      boolean parentBold = parentStyle.isBold();
      boolean parentUnderlined = parentStyle.isUnderlined();
      boolean parentStrikethrough = parentStyle.isStrikethrough();
      boolean parentObfuscated = parentStyle.isObfuscated();
      
      TextColor parentTextColor = parentStyle.getColor();
      if(parentTextColor != null){
         Formatting formatting = Formatting.byName(parentTextColor.getName());
         if(formatting != null){
            parentColor = formatting;
         }
      }
      
      TextContent parentContent = text.getContent();
      if(parentContent instanceof PlainTextContent plainTextContent){
         String contentString = plainTextContent.string();
         
         if(!contentString.isEmpty()){
            codes.add(textToCodeHelper(contentString,parentColor.getName(),parentItalic, parentBold, parentUnderlined, parentStrikethrough, parentObfuscated));
         }
      }
      
      for(Text sibling : text.getSiblings()){
         TextContent siblingContent = sibling.getContent();
         
         if(siblingContent instanceof PlainTextContent plainTextContent){
            String contentString = plainTextContent.string();
            
            if(!contentString.isEmpty()){
               Style siblingStyle = sibling.getStyle();
               
               Formatting color = parentColor;
               TextColor siblingColor = siblingStyle.getColor();
               if(siblingColor != null){
                  Formatting formatting = Formatting.byName(siblingColor.getName());
                  if(formatting != null){
                     color = formatting;
                  }
               }
               codes.add(textToCodeHelper(contentString,color.getName(),
                     siblingStyle.isItalic() || parentItalic,
                     siblingStyle.isBold() || parentBold,
                     siblingStyle.isUnderlined() || parentUnderlined,
                     siblingStyle.isStrikethrough() || parentStrikethrough,
                     siblingStyle.isObfuscated() || parentObfuscated));
            }
         }
      }
      
      if(codes.isEmpty()){
         return "Text.literal(\"\");";
      }else if(codes.size() == 1){
         return codes.getFirst();
      }else{
         String finalCode = "Text.literal(\"\")";
         for(String code : codes){
            finalCode += "\n\t.append("+code.replace(";","")+")";
         }
         return finalCode + ";";
      }
   }
   
   private static String textToCodeHelper(String content, String color, boolean italic, boolean bold, boolean underlined, boolean strikethrough, boolean obfuscated){
      String code = "Text.literal(\""+content+"\")";
      
      code += ".formatted(";
      code += "Formatting."+color.toUpperCase(Locale.ROOT)+",";
      if(italic){
         code += "Formatting.ITALIC,";
      }
      if(bold){
         code += "Formatting.BOLD,";
      }
      if(underlined){
         code += "Formatting.UNDERLINE,";
      }
      if(strikethrough){
         code += "Formatting.STRIKETHROUGH,";
      }
      if(obfuscated){
         code += "Formatting.OBFUSCATED,";
      }
      code = code.substring(0,code.length()-1) + ")";
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

package net.borisshoes.borislib.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtils {
   
   public static MutableComponent getFormattedDimName(ResourceKey<Level> worldKey){
      if(worldKey.identifier().toString().equals(ServerLevel.OVERWORLD.identifier().toString())){
         return Component.literal("Overworld").withStyle(ChatFormatting.GREEN);
      }else if(worldKey.identifier().toString().equals(ServerLevel.NETHER.identifier().toString())){
         return Component.literal("The Nether").withStyle(ChatFormatting.RED);
      }else if(worldKey.identifier().toString().equals(ServerLevel.END.identifier().toString())){
         return Component.literal("The End").withStyle(ChatFormatting.DARK_PURPLE);
      }else{
         return Component.literal(worldKey.identifier().toString()).withStyle(ChatFormatting.YELLOW);
      }
   }
   
   public static void energyBar(ServerPlayer player, double percentage, Component prefix, Component suffix, UnaryOperator<Style> barStyle){
      TextUtils.energyBar(player, percentage, 10, prefix, suffix, barStyle);
   }
   
   public static void energyBar(ServerPlayer player, double percentage, int numBars, Component prefix, Component suffix, UnaryOperator<Style> barStyle){
      MutableComponent text = Component.literal("").append(prefix);
      int value = (int) (percentage * 100);
      char[] unicodeChars = {'▁', '▂', '▃', '▅', '▆', '▇', '▌'};
      for (int i = 0; i < numBars; i++) {
         int segmentValue = value - (i * numBars);
         if (segmentValue <= 0) {
            text.append(Component.literal(String.valueOf(unicodeChars[0])).withStyle(barStyle));
         } else if (segmentValue >= numBars) {
            text.append(Component.literal(String.valueOf(unicodeChars[unicodeChars.length - 1])).withStyle(barStyle));
         } else {
            int charIndex = (int) ((double) segmentValue / numBars * (unicodeChars.length - 1));
            text.append(Component.literal(String.valueOf(unicodeChars[charIndex])).withStyle(barStyle));
         }
      }
      text.append(suffix);
      player.displayClientMessage(text,true);
   }
   
   public static String camelToSnake(String str){
      return str.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase(Locale.ROOT);
   }
   
   public static final ArrayList<Tuple<ChatFormatting,Integer>> COLOR_MAP = new ArrayList<>(Arrays.asList(
         new Tuple<>(ChatFormatting.BLACK,0x000000),
         new Tuple<>(ChatFormatting.DARK_BLUE,0x0000AA),
         new Tuple<>(ChatFormatting.DARK_GREEN,0x00AA00),
         new Tuple<>(ChatFormatting.DARK_AQUA,0x00AAAA),
         new Tuple<>(ChatFormatting.DARK_RED,0xAA0000),
         new Tuple<>(ChatFormatting.DARK_PURPLE,0xAA00AA),
         new Tuple<>(ChatFormatting.GOLD,0xFFAA00),
         new Tuple<>(ChatFormatting.GRAY,0xAAAAAA),
         new Tuple<>(ChatFormatting.DARK_GRAY,0x555555),
         new Tuple<>(ChatFormatting.BLUE,0x5555FF),
         new Tuple<>(ChatFormatting.GREEN,0x55FF55),
         new Tuple<>(ChatFormatting.AQUA,0x55FFFF),
         new Tuple<>(ChatFormatting.RED,0xFF5555),
         new Tuple<>(ChatFormatting.LIGHT_PURPLE,0xFF55FF),
         new Tuple<>(ChatFormatting.YELLOW,0xFFFF55),
         new Tuple<>(ChatFormatting.WHITE,0xFFFFFF)
   ));
   
   public static ChatFormatting getClosestFormatting(int colorRGB){
      ChatFormatting closest = ChatFormatting.WHITE;
      double cDist = Integer.MAX_VALUE;
      for(Tuple<ChatFormatting, Integer> pair : COLOR_MAP){
         int repColor = pair.getB();
         double rDist = (((repColor>>16)&0xFF)-((colorRGB>>16)&0xFF))*0.30;
         double gDist = (((repColor>>8)&0xFF)-((colorRGB>>8)&0xFF))*0.59;
         double bDist = ((repColor&0xFF)-(colorRGB&0xFF))*0.11;
         double dist = rDist*rDist + gDist*gDist + bDist*bDist;
         if(dist < cDist){
            cDist = dist;
            closest = pair.getA();
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
   
   public static MutableComponent removeItalics(Component text){
      return removeItalics(Component.literal("").append(text));
   }
   
   public static MutableComponent removeItalics(MutableComponent text){
      Style parentStyle = Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(false).withBold(false).withUnderlined(false).withObfuscated(false).withStrikethrough(false);
      return text.setStyle(text.getStyle().applyTo(parentStyle));
   }
   
   public static MutableComponent parseString(String input){
      ArrayList<String> matchList = new ArrayList<>();
      MutableComponent text = Component.literal("");
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
         ChatFormatting formatting = ChatFormatting.getByName(parentTextColor.serialize());
         if(formatting != null){
            parentColor = formatting.getChar();
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
                  ChatFormatting formatting = ChatFormatting.getByName(siblingColor.serialize());
                  if(formatting != null){
                     color = formatting.getChar();
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
         ChatFormatting formatting = ChatFormatting.getByName(parentTextColor.serialize());
         if(formatting != null){
            parentColor = formatting;
         }
      }
      
      ComponentContents parentContent = text.getContents();
      if(parentContent instanceof PlainTextContents plainTextContent){
         String contentString = plainTextContent.text();
         
         if(!contentString.isEmpty()){
            codes.add(textToCodeHelper(contentString,parentColor.getName(),parentItalic, parentBold, parentUnderlined, parentStrikethrough, parentObfuscated));
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
                  ChatFormatting formatting = ChatFormatting.getByName(siblingColor.serialize());
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

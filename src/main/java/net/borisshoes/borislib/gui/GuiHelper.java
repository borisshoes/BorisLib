package net.borisshoes.borislib.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.borisshoes.borislib.utils.AlgoUtils;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class GuiHelper {
   
   public static void outlineGUI(SimpleGui gui, int color, Text borderText){
      outlineGUI(gui,color,borderText,null);
   }
   
   public static void outlineGUI(SimpleGui gui, int color, Text borderText, List<Text> lore){
      int width = gui.getWidth();
      int height = gui.getHeight();
      outlineGUI(width, height, gui,color,borderText,lore);
   }
   
   public static void outlineGUI(int width, int height, SimpleGui gui, int color, Text borderText, List<Text> lore){
      int size = width*height;
      for(int i = 0; i < size; i++){
         gui.clearSlot(i);
         GuiElementBuilder menuItem;
         boolean top = i/width == 0;
         boolean bottom = i/width == (size/width - 1);
         boolean left = i%width == 0;
         boolean right = i%width == width-1;
         
         if(top){
            if(left){
               menuItem = GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.MENU_TOP_LEFT,color));
            }else if(right){
               menuItem = GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.MENU_TOP_RIGHT,color));
            }else{
               menuItem = GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.MENU_TOP,color));
            }
         }else if(bottom){
            if(left){
               menuItem = GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.MENU_BOTTOM_LEFT,color));
            }else if(right){
               menuItem = GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.MENU_BOTTOM_RIGHT,color));
            }else{
               menuItem = GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.MENU_BOTTOM,color));
            }
         }else if(left){
            menuItem = GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.MENU_LEFT,color));
         }else if(right){
            menuItem = GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.MENU_RIGHT,color));
         }else{
            menuItem = GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.MENU_TOP,color));
         }
         
         if(borderText.getString().isEmpty()){
            menuItem.hideTooltip();
         }else{
            menuItem.setName(borderText).hideDefaultTooltip();
            if(lore != null && !lore.isEmpty()){
               for(Text text : lore){
                  menuItem.addLoreLine(text);
               }
            }
         }
         
         gui.setSlot(i,menuItem);
      }
   }
}


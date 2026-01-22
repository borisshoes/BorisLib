package net.borisshoes.borislib.gui;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.borisshoes.borislib.BorisLib;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.HashMap;
import java.util.Map;

import static net.borisshoes.borislib.BorisLib.*;

public class GraphicalItem extends Item implements PolymerItem {
   
   public static final String GRAPHICS_TAG = "graphic_id";
   
   public static final GraphicElement CONFIRM = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "confirm"), Items.STRUCTURE_VOID, false));
   public static final GraphicElement CONFIRM_COLOR = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "confirm_color"), Items.STRUCTURE_VOID, true));
   public static final GraphicElement CANCEL = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "cancel"), Items.BARRIER, false));
   public static final GraphicElement CANCEL_COLOR = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "cancel_color"), Items.BARRIER, true));
   public static final GraphicElement LEFT_ARROW = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "left_arrow"), Items.SPECTRAL_ARROW, false));
   public static final GraphicElement RIGHT_ARROW = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "right_arrow"), Items.SPECTRAL_ARROW, false));
   public static final GraphicElement ARROW_UP = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "arrow_up"), Items.SPECTRAL_ARROW, true));
   public static final GraphicElement ARROW_DOWN = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "arrow_down"), Items.SPECTRAL_ARROW, true));
   public static final GraphicElement ARROW_LEFT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "arrow_left"), Items.SPECTRAL_ARROW, true));
   public static final GraphicElement ARROW_RIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "arrow_right"), Items.SPECTRAL_ARROW, true));
   public static final GraphicElement SORT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "sort"), Items.NETHER_STAR, false));
   public static final GraphicElement FILTER = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "filter"), Items.HOPPER, false));
   public static final GraphicElement BLACK = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "black"), Items.BLACK_DYE, false));
   public static final GraphicElement EMPTY = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "empty"), Items.GLASS_PANE, true));
   public static final GraphicElement ORB = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "orb"), Items.WHITE_CONCRETE, true));
   public static final GraphicElement REFRESH = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "refresh"), Items.MUSIC_DISC_CAT, true));
   public static final GraphicElement MENU_HORIZONTAL = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_horizontal"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_HORIZONTAL_INVERTED = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_horizontal_inverted"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_VERTICAL = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_vertical"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_VERTICAL_INVERTED = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_vertical_inverted"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_MIDDLE = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_middle"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_MIDDLE_INVERTED = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_middle_inverted"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_LEFT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_left"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_RIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_right"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_RIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_right"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_RIGHT_DARK = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_right_dark"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_RIGHT_LIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_right_light"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_RIGHT_INVERTED = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_right_inverted"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_LEFT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_left"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_LEFT_DARK = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_left_dark"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_LEFT_LIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_left_light"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_LEFT_INVERTED = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_left_inverted"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_LEFT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_left"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_LEFT_DARK = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_left_dark"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_LEFT_LIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_left_light"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_LEFT_INVERTED = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_left_inverted"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_RIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_right"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_RIGHT_DARK = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_right_dark"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_RIGHT_LIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_right_light"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_RIGHT_INVERTED = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_right_inverted"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_RIGHT_CONNECTOR = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_right_connector"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_RIGHT_CONNECTOR_DARK = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_right_connector_dark"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_RIGHT_CONNECTOR_LIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_right_connector_light"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_RIGHT_CONNECTOR_INVERTED = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_right_connector_inverted"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_LEFT_CONNECTOR = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_left_connector"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_LEFT_CONNECTOR_DARK = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_left_connector_dark"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_LEFT_CONNECTOR_LIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_left_connector_light"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_LEFT_CONNECTOR_INVERTED = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_left_connector_inverted"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_CONNECTOR = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_connector"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_CONNECTOR_DARK = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_connector_dark"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_CONNECTOR_LIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_connector_light"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_CONNECTOR_INVERTED = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_connector_inverted"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_CONNECTOR = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_connector"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_CONNECTOR_DARK = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_connector_dark"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_CONNECTOR_LIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_connector_light"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_CONNECTOR_INVERTED = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_connector_inverted"), Items.GLASS_PANE, true));
   public static final GraphicElement PAGE_BG = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "page_bg"), Items.GLASS_PANE, true));
   
   // Maps base items to their colored variant suffix pattern
   // Format: base item -> (naming pattern, uses "stained" prefix for glass types)
   private static final Map<Item, String> COLORABLE_ITEM_PATTERNS = Map.ofEntries(
         Map.entry(Items.GLASS_PANE, "stained_glass_pane"),
         Map.entry(Items.GLASS, "stained_glass"),
         Map.entry(Items.WHITE_DYE, "dye"),
         Map.entry(Items.WHITE_WOOL, "wool"),
         Map.entry(Items.WHITE_CONCRETE, "concrete"),
         Map.entry(Items.WHITE_CONCRETE_POWDER, "concrete_powder"),
         Map.entry(Items.TERRACOTTA, "terracotta"),
         Map.entry(Items.WHITE_GLAZED_TERRACOTTA, "glazed_terracotta"),
         Map.entry(Items.WHITE_CARPET, "carpet"),
         Map.entry(Items.WHITE_BED, "bed"),
         Map.entry(Items.WHITE_BANNER, "banner"),
         Map.entry(Items.WHITE_CANDLE, "candle"),
         Map.entry(Items.WHITE_SHULKER_BOX, "shulker_box")
   );
   
   // RGB values for each dye color used in color matching
   private static final Map<DyeColor, Integer> DYE_COLOR_RGB = Map.ofEntries(
         Map.entry(DyeColor.BLACK, 0x000000),
         Map.entry(DyeColor.BLUE, 0x0000ff),
         Map.entry(DyeColor.BROWN, 0x6b5341),
         Map.entry(DyeColor.GRAY, 0x5c5c5c),
         Map.entry(DyeColor.CYAN, 0x168e94),
         Map.entry(DyeColor.GREEN, 0x04753a),
         Map.entry(DyeColor.LIGHT_BLUE, 0x5ad2fa),
         Map.entry(DyeColor.LIGHT_GRAY, 0xc7c7c7),
         Map.entry(DyeColor.LIME, 0x4ded0e),
         Map.entry(DyeColor.MAGENTA, 0xb306c9),
         Map.entry(DyeColor.ORANGE, 0xff8800),
         Map.entry(DyeColor.PINK, 0xff7dde),
         Map.entry(DyeColor.PURPLE, 0x8502cc),
         Map.entry(DyeColor.RED, 0xff0000),
         Map.entry(DyeColor.WHITE, 0xffffff),
         Map.entry(DyeColor.YELLOW, 0xffff00)
   );
   
   public static final Map<DyeColor, Map<Item, Item>> COLORED_ITEMS = buildColoredItemsMap();
   
   private static Map<DyeColor, Map<Item, Item>> buildColoredItemsMap() {
      Map<DyeColor, Map<Item, Item>> result = new HashMap<>();
      for (DyeColor color : DyeColor.values()) {
         Map<Item, Item> colorMap = new HashMap<>();
         String colorName = color.getSerializedName();
         for (Map.Entry<Item, String> entry : COLORABLE_ITEM_PATTERNS.entrySet()) {
            Item baseItem = entry.getKey();
            String pattern = entry.getValue();
            Identifier coloredId = Identifier.fromNamespaceAndPath("minecraft", colorName + "_" + pattern);
            Item coloredItem = BuiltInRegistries.ITEM.getValue(coloredId);
            if (coloredItem != Items.AIR) {
               colorMap.put(baseItem, coloredItem);
            }
         }
         result.put(color, colorMap);
      }
      return result;
   }
   
   public GraphicalItem(Item.Properties settings){
      super(settings.setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID,"graphical_item"))));
   }
   
   @Override
   public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context){
      String id = BORISLIB_ITEM_DATA.getStringProperty(stack,GRAPHICS_TAG);
      Identifier identifier = Identifier.tryParse(id);
      GraphicElement elem = BorisLib.GRAPHIC_ITEM_REGISTRY.getValue(identifier);
      if(elem == null){
         return Identifier.fromNamespaceAndPath(MOD_ID, "graphical_item");
      }
      
      if(PolymerResourcePackUtils.hasMainPack(context)){
         return Identifier.fromNamespaceAndPath(elem.id().getNamespace(),"gui/"+elem.id().getPath());
      }else{
         return BuiltInRegistries.ITEM.getResourceKey(getPolymerItem(stack,context)).get().identifier();
      }
   }
   
   @Override
   public Item getPolymerItem(ItemStack itemStack, PacketContext context){
      String id = BORISLIB_ITEM_DATA.getStringProperty(itemStack,GRAPHICS_TAG);
      Identifier identifier = Identifier.tryParse(id);
      GraphicElement elem = BorisLib.GRAPHIC_ITEM_REGISTRY.getValue(identifier);
      if(elem == null || itemStack == null) return Items.BARRIER;
      if(!elem.dyeable() || !itemStack.has(DataComponents.DYED_COLOR)) return elem.replacement();
      
      if(PolymerResourcePackUtils.hasMainPack(context.getPlayer())){
         return Items.LEATHER_CHESTPLATE;
      }else{
         return getItemFromColor(elem.replacement(), itemStack.get(DataComponents.DYED_COLOR).rgb());
      }
   }
   
   private Item getItemFromColor(Item baseItem, int colorRGB){
      DyeColor closestColor = DyeColor.WHITE;
      double closestDist = Double.MAX_VALUE;
      
      for(Map.Entry<DyeColor, Integer> entry : DYE_COLOR_RGB.entrySet()){
         int repColor = entry.getValue();
         double rDist = (((repColor>>16)&0xFF)-((colorRGB>>16)&0xFF))*0.30;
         double gDist = (((repColor>>8)&0xFF)-((colorRGB>>8)&0xFF))*0.59;
         double bDist = ((repColor&0xFF)-(colorRGB&0xFF))*0.11;
         double dist = rDist*rDist + gDist*gDist + bDist*bDist;
         if(dist < closestDist){
            closestDist = dist;
            closestColor = entry.getKey();
         }
      }
      
      Map<Item, Item> colorMap = COLORED_ITEMS.get(closestColor);
      if(colorMap != null && colorMap.containsKey(baseItem)){
         return colorMap.get(baseItem);
      }
      return baseItem;
   }
   
   public static ItemStack with(GraphicElement id){
      ItemStack stack = new ItemStack(BorisLib.GRAPHICAL_ITEM);
      BORISLIB_ITEM_DATA.putProperty(stack,GRAPHICS_TAG,id.id().toString());
      return stack;
   }
   
   public static ItemStack withColor(GraphicElement id, int color){
      ItemStack stack = new ItemStack(BorisLib.GRAPHICAL_ITEM);
      BORISLIB_ITEM_DATA.putProperty(stack,GRAPHICS_TAG,id.id().toString());
      stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
      stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.DYED_COLOR,true));
      return stack;
   }
   
   // These are not quite working yet with how client-sided bundles are ;-;
   
//   public static ItemStack getItemBlack(ItemStack shownItem, boolean selected, int selectionColor){
//      ItemStack stack = with(BLACK);
//      CustomModelDataComponent custom = new CustomModelDataComponent(List.of(),List.of(),List.of("selected"),List.of(selectionColor));
//      BundleContentsComponent.Builder bundle = new BundleContentsComponent.Builder(new BundleContentsComponent(List.of(shownItem.copy())));
//      bundle.setSelectedStackIndex(0);
//      if(selected) stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, custom);
//      if(!shownItem.isEmpty()) stack.set(DataComponentTypes.BUNDLE_CONTENTS, bundle.build());
//      stack.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.BUNDLE_CONTENTS,false));
//      return stack;
//   }
//
//   public static ItemStack getItemPageBg(int pageColor, ItemStack shownItem, boolean selected, int selectionColor){
//      ItemStack stack = withColor(PAGE_BG,pageColor);
//      CustomModelDataComponent custom = new CustomModelDataComponent(List.of(),List.of(true),List.of("selected"),List.of(selectionColor));
//      BundleContentsComponent.Builder bundle = new BundleContentsComponent.Builder(new BundleContentsComponent(List.of(shownItem.copy())));
//      bundle.setSelectedStackIndex(0);
//      if(selected) stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, custom);
//      if(!shownItem.isEmpty()) stack.set(DataComponentTypes.BUNDLE_CONTENTS, bundle.build());
//      stack.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.DYED_COLOR,true).with(DataComponentTypes.BUNDLE_CONTENTS,false));
//      return stack;
//   }
   
   
   
   public record GraphicElement(Identifier id, Item replacement, boolean dyeable){}
}
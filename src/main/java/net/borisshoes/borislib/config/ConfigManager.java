package net.borisshoes.borislib.config;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.borisshoes.borislib.config.values.ListConfigValue;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.permissions.PermissionLevel;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static net.borisshoes.borislib.BorisLib.LOGGER;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ConfigManager {
   public Set<ConfigValue> values;
   private final Map<String, ConfigValue> nameIndex = new HashMap<>();
   private final File file;
   private final String modId;
   private final String modName;
   
   public ConfigManager(String modId, String modName, String fileName, Registry<IConfigSetting<?>> configRegistry){
      this.modId = modId;
      this.modName = modName;
      this.file = FabricLoader.getInstance().getConfigDir().resolve(fileName).toFile();
      this.values = configRegistry.stream().map(IConfigSetting::makeConfigValue).collect(Collectors.toCollection(LinkedHashSet::new));
      rebuildIndex();
      this.read();
      this.save();
   }
   
   private void rebuildIndex(){
      nameIndex.clear();
      for(ConfigValue value : values){
         nameIndex.put(value.name, value);
      }
   }
   
   public void read(){
      try(BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(file)))){
         LOGGER.debug("Reading {} config...", modName);
         
         for(ConfigValue value : this.values){
            value.setValue(value.defaultValue);
         }
         
         while(input.ready()){
            String configLine = input.readLine();
            String trimmed = configLine.trim();
            if(trimmed.isEmpty()) continue;
            char firstChar = trimmed.charAt(0);
            
            if(firstChar == '!' || firstChar == '#') continue;
            if(!configLine.contains("=")) continue;
            
            int splitIndex = configLine.indexOf('=');
            String valueName = configLine.substring(0, splitIndex).strip();
            String valueValue = configLine.substring(splitIndex + 1).strip();
            
            ConfigValue value = nameIndex.get(valueName);
            if(value != null){
               Object defaultValue = value.defaultValue;
               try{
                  Object parsed = value.getFromString(valueValue);
                  value.setValue(parsed != null ? parsed : defaultValue);
               }catch(Exception e){
                  value.setValue(defaultValue);
               }
            }
         }
         
      }catch(FileNotFoundException ignored){
         LOGGER.debug("Initialising {} config...", modName);
         this.values.forEach(value -> value.value = value.defaultValue);
      }catch(IOException e){
         LOGGER.fatal("Failed to load {} config file!", modName);
         LOGGER.fatal(e);
      }catch(Exception e){
         LOGGER.fatal("Failed to parse {} config", modName);
         LOGGER.fatal(e);
      }
   }
   
   public void save(){
      LOGGER.debug("Updating {} config...", modName);
      try(BufferedWriter output = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)))){
         output.write("# " + modName + " Configuration File" + " | " + new Date());
         output.newLine();
         output.newLine();
         
         for(ConfigValue value : this.values){
            if(value.getComment(this.modId) != null){
               output.write("# " + Component.translatable(value.getComment(this.modId)).getString());
               output.newLine();
            }
            output.write(value.name + " = " + value.getValueString());
            output.newLine();
         }
      }catch(IOException e){
         LOGGER.fatal("Failed to save {} config file!", modName);
         LOGGER.fatal(e);
      }
   }
   
   public LiteralArgumentBuilder<CommandSourceStack> generateCommand(String prefixA, String prefixB){
      LiteralArgumentBuilder<CommandSourceStack> root;
      if(!prefixB.isBlank()){
         root = literal(prefixA).then(literal(prefixB).requires(Permissions.require(modId + ".config", PermissionLevel.GAMEMASTERS))
               .executes(ctx -> {
                  values.forEach(value ->
                        ctx.getSource().sendSuccess(() -> MutableComponent.create(new TranslatableContents(ConfigValue.getTranslation(value.getName(), this.modId, "getter_setter"), null, new String[]{String.valueOf(value.getValueString())})), false));
                  return 1;
               }));
      }else{
         root = literal(prefixA).requires(Permissions.require(modId + ".config", PermissionLevel.GAMEMASTERS))
               .executes(ctx -> {
                  values.forEach(value ->
                        ctx.getSource().sendSuccess(() -> MutableComponent.create(new TranslatableContents(ConfigValue.getTranslation(value.getName(), this.modId, "getter_setter"), null, new String[]{String.valueOf(value.getValueString())})), false));
                  return 1;
               });
      }
      values.forEach(value -> {
         LiteralArgumentBuilder<CommandSourceStack> valueArg = literal(value.name)
               .requires(Permissions.require(modId + ".config." + value.name.toLowerCase(Locale.ROOT) + ".get", PermissionLevel.GAMEMASTERS))
               .executes(ctx -> {
                  ctx.getSource().sendSuccess(() -> MutableComponent.create(new TranslatableContents(ConfigValue.getTranslation(value.getName(), this.modId, "getter_setter"), null, new String[]{String.valueOf(value.getValueString())})), false);
                  return 1;
               })
               .then(argument(value.name, value.getArgumentType()).suggests(value::getSuggestions)
                     .requires(Permissions.require(modId + ".config." + value.name.toLowerCase(Locale.ROOT) + ".set", PermissionLevel.GAMEMASTERS))
                     .executes(ctx -> {
                        Object parsed = value.parseArgumentValue(ctx);
                        boolean pass = value.validate(value.parseArgumentValue(ctx), ctx);
                        if(!pass) return 0;
                        value.setValue(parsed);
                        ((CommandContext<CommandSourceStack>) ctx).getSource().sendSuccess(() -> MutableComponent.create(new TranslatableContents(ConfigValue.getTranslation(value.getName(), this.modId, "getter_setter"), null, new String[]{String.valueOf(value.getValueString())})), true);
                        this.save();
                        return 1;
                     }));
         if(!prefixB.isBlank()){
            root.then(literal(prefixB).then(valueArg));
         }else{
            root.then(valueArg);
         }
      });
      return root;
   }
   
   
   public Object getValue(String name){
      ConfigValue cv = nameIndex.get(name);
      if(cv == null) return null;
      return cv.value != null ? cv.value : cv.defaultValue;
   }
   
   public Object getValue(IConfigSetting<?> setting){
      try{
         return this.getValue(setting.getName());
      }catch(Exception e){
         LOGGER.error(e.toString());
      }
      return null;
   }
   
   public int getInt(IConfigSetting<?> setting){
      try{
         Object value = this.getValue(setting.getName());
         if(value instanceof Number number){
            return number.intValue();
         }
         if(value instanceof String s){
            return (int) Double.parseDouble(s);
         }
         LOGGER.error("Config {}:{} is not numeric for getInt (type={})", this.modId, setting.getName(), value != null ? value.getClass().getName() : "null");
      }catch(Exception e){
         LOGGER.error("Failed to get Integer config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return 0;
   }
   
   public float getFloat(IConfigSetting<?> setting){
      try{
         Object value = this.getValue(setting.getName());
         if(value instanceof Number number){
            return number.floatValue();
         }
         if(value instanceof String s){
            return Float.parseFloat(s);
         }
         LOGGER.error("Config {}:{} is not numeric for getFloat (type={})", this.modId, setting.getName(), value != null ? value.getClass().getName() : "null");
      }catch(Exception e){
         LOGGER.error("Failed to get Float config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return 0;
   }
   
   public boolean getBoolean(IConfigSetting<?> setting){
      try{
         return (boolean) this.getValue(setting.getName());
      }catch(Exception e){
         LOGGER.error("Failed to get Boolean config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return false;
   }
   
   public double getDouble(IConfigSetting<?> setting){
      try{
         Object value = this.getValue(setting.getName());
         if(value instanceof Number number){
            return number.doubleValue();
         }
         if(value instanceof String s){
            return Double.parseDouble(s);
         }
         LOGGER.error("Config {}:{} is not numeric for getDouble (type={})", this.modId, setting.getName(), value != null ? value.getClass().getName() : "null");
      }catch(Exception e){
         LOGGER.error("Failed to get Double config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return 0;
   }
   
   public String getString(IConfigSetting<?> setting){
      try{
         Object value = this.getValue(setting.getName());
         if(value instanceof String s){
            return s;
         }
         if(value != null){
            return String.valueOf(value);
         }
         LOGGER.error("Config {}:{} is null for getString", this.modId, setting.getName());
      }catch(Exception e){
         LOGGER.error("Failed to get String config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return "";
   }
   
   public <T> List<T> getList(IConfigSetting<?> setting){
      try{
         Object value = this.getValue(setting.getName());
         if(value instanceof List<?> list){
            return (List<T>) list;
         }
         LOGGER.error("Config {}:{} is not a List for getList (type={})", this.modId, setting.getName(), value != null ? value.getClass().getName() : "null");
      }catch(Exception e){
         LOGGER.error("Failed to get List config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return List.of();
   }
   
   public List<String> getStringList(IConfigSetting<?> setting){
      try{
         List<?> raw = getList(setting);
         List<String> result = new ArrayList<>();
         for(Object o : raw){
            result.add(String.valueOf(o));
         }
         return result;
      }catch(Exception e){
         LOGGER.error("Failed to get String List config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return List.of();
   }
   
   public List<Integer> getIntList(IConfigSetting<?> setting){
      try{
         List<?> raw = getList(setting);
         List<Integer> result = new ArrayList<>();
         for(Object o : raw){
            if(o instanceof Number n){
               result.add(n.intValue());
            }else if(o instanceof String s){
               try{
                  result.add((int) Double.parseDouble(s));
               }catch(NumberFormatException ignored){
               }
            }
         }
         return result;
      }catch(Exception e){
         LOGGER.error("Failed to get Integer List config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return List.of();
   }
   
   public List<Double> getDoubleList(IConfigSetting<?> setting){
      try{
         List<?> raw = getList(setting);
         List<Double> result = new ArrayList<>();
         for(Object o : raw){
            if(o instanceof Number n){
               result.add(n.doubleValue());
            }else if(o instanceof String s){
               try{
                  result.add(Double.parseDouble(s));
               }catch(NumberFormatException ignored){
               }
            }
         }
         return result;
      }catch(Exception e){
         LOGGER.error("Failed to get Double List config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return List.of();
   }
   
   public List<Float> getFloatList(IConfigSetting<?> setting){
      try{
         List<?> raw = getList(setting);
         List<Float> result = new ArrayList<>();
         for(Object o : raw){
            if(o instanceof Number n){
               result.add(n.floatValue());
            }else if(o instanceof String s){
               try{
                  result.add(Float.parseFloat(s));
               }catch(NumberFormatException ignored){
               }
            }
         }
         return result;
      }catch(Exception e){
         LOGGER.error("Failed to get Float List config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return List.of();
   }
   
   public List<Boolean> getBooleanList(IConfigSetting<?> setting){
      try{
         List<?> raw = getList(setting);
         List<Boolean> result = new ArrayList<>();
         for(Object o : raw){
            if(o instanceof Boolean b){
               result.add(b);
            }else if(o instanceof String s){
               result.add(Boolean.parseBoolean(s));
            }
         }
         return result;
      }catch(Exception e){
         LOGGER.error("Failed to get Boolean List config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return List.of();
   }
   
   public <E extends Enum<E>> List<E> getEnumList(IConfigSetting<?> setting, Class<E> enumClass){
      try{
         List<?> raw = getList(setting);
         List<E> result = new ArrayList<>();
         for(Object o : raw){
            if(enumClass.isInstance(o)){
               result.add(enumClass.cast(o));
            }else if(o instanceof String s){
               try{
                  result.add(Enum.valueOf(enumClass, s.toUpperCase(Locale.ROOT)));
               }catch(IllegalArgumentException ignored){
               }
            }
         }
         return result;
      }catch(Exception e){
         LOGGER.error("Failed to get Enum List config for {}:{}", this.modId, setting.getName());
         LOGGER.error(e.toString());
      }
      return List.of();
   }
}

package net.borisshoes.borislib.config;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static net.borisshoes.borislib.BorisLib.LOGGER;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ConfigManager {
   public Set<ConfigValue> values;
   private final File file;
   private final String modId;
   private final String modName;
   
   public ConfigManager(String modId, String modName, String fileName, Registry<IConfigSetting<?>> configRegistry){
      this.modId = modId;
      this.modName = modName;
      this.file = FabricLoader.getInstance().getConfigDir().resolve(fileName).toFile();
      this.values = configRegistry.stream().map(IConfigSetting::makeConfigValue).collect(Collectors.toCollection(HashSet::new));
      this.read();
      this.save();
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
            
            for(ConfigValue value : this.values){
               if(!valueName.equals(value.name)) continue;
               Object defaultValue = value.defaultValue;
               try{
                  value.setValue(value.getFromString(valueValue));
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
         root = literal(prefixA).then(literal(prefixB).requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
               .executes(ctx -> {
                  values.forEach(value ->
                        ctx.getSource().sendSuccess(() -> MutableComponent.create(new TranslatableContents(ConfigValue.getTranslation(value.getName(), this.modId, "getter_setter"), null, new String[]{String.valueOf(value.getValueString())})), false));
                  return 1;
               }));
      }else{
         root = literal(prefixA).requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
               .executes(ctx -> {
                  values.forEach(value ->
                        ctx.getSource().sendSuccess(() -> MutableComponent.create(new TranslatableContents(ConfigValue.getTranslation(value.getName(), this.modId, "getter_setter"), null, new String[]{String.valueOf(value.getValueString())})), false));
                  return 1;
               });
      }
      values.forEach(value -> {
            LiteralArgumentBuilder<CommandSourceStack> valueArg = literal(value.name)
                  .executes(ctx -> {
                     ctx.getSource().sendSuccess(() -> MutableComponent.create(new TranslatableContents(ConfigValue.getTranslation(value.getName(), this.modId, "getter_setter"), null, new String[]{String.valueOf(value.getValueString())})), false);
                     return 1;
                  })
                  .then(argument(value.name, value.getArgumentType()).suggests(value::getSuggestions)
                        .executes(ctx -> {
                           value.value = value.parseArgumentValue(ctx);
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
      return values.stream().filter(value -> value.name.equals(name)).findFirst().map(iConfigValue -> iConfigValue.value).orElse(null);
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
}

package net.borisshoes.borislib.config;

public interface IConfigSetting<T>{
   ConfigValue<T> makeConfigValue();
   String getId();
   String getName();
}
package org.ultramine.mods.anticheat;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLModIdMappingEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import org.apache.commons.io.FileUtils;
import org.ultramine.core.service.InjectService;
import org.ultramine.core.service.ServiceManager;
import org.ultramine.core.util.Undoable;
import org.ultramine.server.ConfigurationHandler;
import org.ultramine.server.chunk.AntiXRayService;
import org.ultramine.server.util.Resources;
import org.ultramine.server.util.YamlConfigProvider;

import java.io.File;
import java.io.IOException;

@Mod(modid = "UM-AntiCheat", name = "UM-AntiCheat", version = "@version@", acceptableRemoteVersions = "*")
public class UMAntiCheat
{
	@InjectService private static ServiceManager services;
	private static UMAntiCheat instance;

	private Undoable onUnload;

	public static UMAntiCheat instance()
	{
		return instance;
	}

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent e)
	{
		instance = this;
	}

	@Mod.EventHandler
	public void serverStarting(FMLServerStartingEvent e)
	{
		e.registerCommands(UMACCommands.class);
//		reload();
	}

	@Mod.EventHandler
	public void remap(FMLModIdMappingEvent e)
	{
		reload();
	}

	public void reload()
	{
		if(onUnload != null)
		{
			onUnload.undo();
			onUnload = null;
		}

		UMACConfig config = loadConfig();
		if(config.antiXRay.enabled)
			onUnload = services.register(AntiXRayService.class, new AntiXRayServiceImpl(config.antiXRay), 100);
	}

	private UMACConfig loadConfig()
	{
		File file = new File(ConfigurationHandler.getSettingDir(), "anticheat.yml");
		if(file.exists())
		{
			return YamlConfigProvider.readConfig(file, UMACConfig.class);
		}
		else
		{
			String configStr = Resources.getAsString("/assets/um-anticheat/defaults/default-config.yml");
			try {
				FileUtils.writeStringToFile(file, configStr);
			} catch(IOException e) {
				throw new RuntimeException("Failed to create configuration file: " + file.getAbsolutePath(), e);
			}
			return YamlConfigProvider.readConfig(configStr, UMACConfig.class);
		}
	}
}

package main.java.buildbot.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import main.java.buildbot.Buildbot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ConfigGuiFactory implements IModGuiFactory {
	@SideOnly(Side.CLIENT)
	public static class ConfigGuiScreen extends GuiConfig {

		public static final String LANGKEY_CONFIG = "buildbot.config.title";

		public ConfigGuiScreen(GuiScreen parent) {
			super(parent, getConfigElements(), Buildbot.MODID, false, false, I18n.format(LANGKEY_CONFIG));
			Buildbot.LOGGER.debug("config GUI initialized");
		}

		private static List<IConfigElement> getConfigElements() {
			ConfigManager configs = ConfigManager.get();
			List<IConfigElement> list = new ArrayList<>();
			list.add(new ConfigElement(configs.TIMEOUT));
			list.add(new ConfigElement(configs.DELAY));
			list.add(new ConfigElement(configs.LOOKSPD));
			return list;
		}
	}

	@Override
	public void initialize(Minecraft minecraft) {}

	@Override
	public Class<? extends GuiScreen> mainConfigGuiClass() {
		return FMLCommonHandler.instance().getSide().isClient() ? ConfigGuiScreen.class : null;
	}

	@Override
	public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
		return null;
	}

	@SuppressWarnings("deprecation")
	@Override
	public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
		return null;
	}

}

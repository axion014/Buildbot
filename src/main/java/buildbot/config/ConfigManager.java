package main.java.buildbot.config;

import main.java.buildbot.Buildbot;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ConfigManager {
	private static ConfigManager instance;
	private static final String CONFIG_CATEGORY_GENERAL = "general";
	public final Property TIMEOUT;
	public final Property DELAY;
	public final Property LOOKSPD;

	private final Configuration config;

	public ConfigManager(Configuration config) {
		this.config = config;
		TIMEOUT = config.get(CONFIG_CATEGORY_GENERAL, "Timeout", Buildbot.DEFAULT_TIMEOUT)
				.setLanguageKey("buildbot.config.timeout");
		DELAY = config.get(CONFIG_CATEGORY_GENERAL, "Delay", Buildbot.DEFAULT_DELAY)
				.setLanguageKey("buildbot.config.delay");
		LOOKSPD = config.get(CONFIG_CATEGORY_GENERAL, "Look speed", Buildbot.DEFAULT_LOOK_SPEED)
				.setLanguageKey("buildbot.config.lookspeed");
	}

	public int getPropTimeout() {
		return TIMEOUT.getInt();
	}
	
	public int getPropDelay() {
		return DELAY.getInt();
	}
	
	public double getPropLookspeed() {
		return LOOKSPD.getDouble();
	}

	public static ConfigManager init(Configuration config) {
		if (instance != null) throw new IllegalStateException();
		instance = new ConfigManager(config);
		return instance;
	}

	public static ConfigManager get() {
		if (instance == null) throw new IllegalStateException();
		return instance;
	}

	public void save() {
		config.save();
	}
}

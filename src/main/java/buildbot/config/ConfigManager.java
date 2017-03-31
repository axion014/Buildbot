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
	private static final String[] LANGKEY_ENABLE_DISABLE = {"buildbot.config.enable", "buildbot.config.disable"};

	public final Property ENABLE;
	public final Property SOURCE;
	public final Property TIMEOUT;
	public final Property LOOKSPD;

	private final Configuration config;

	public ConfigManager(Configuration config) {
		this.config = config;
		ENABLE = config.get(CONFIG_CATEGORY_GENERAL, " ", LANGKEY_ENABLE_DISABLE[0],
			"means this mod's effectiveness. value is \"buildbot.config.\"(enable or disable).",
			LANGKEY_ENABLE_DISABLE);
		SOURCE = config.get(CONFIG_CATEGORY_GENERAL, "Source", Buildbot.DEFAULT_SOURCE)
				.setLanguageKey("buildbot.config.source");
		TIMEOUT = config.get(CONFIG_CATEGORY_GENERAL, "Timeout", Buildbot.DEFAULT_TIMEOUT)
				.setLanguageKey("buildbot.config.timeout");
		LOOKSPD = config.get(CONFIG_CATEGORY_GENERAL, "Look speed", Buildbot.DEFAULT_LOOK_SPEED)
				.setLanguageKey("buildbot.config.lookspeed");
	}

	public boolean getPropEnable() {
		return ENABLE.getString().equals(LANGKEY_ENABLE_DISABLE[0]);
	}

	public String getPropSource() {
		return SOURCE.getString();
	}

	public int getPropTimeout() {
		return TIMEOUT.getInt();
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

package main.java.buildbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClickHelper {

	private static Minecraft minecraft = Minecraft.getMinecraft();
	private static boolean placeing = false;
	private static boolean attacking = false;

	private ClickHelper() {}

	public static void setPlaceing(boolean flag) {
		KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindUseItem.getKeyCode(), flag);
		placeing = flag;
	}

	public static void setAttacking(boolean flag) {
		KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindAttack.getKeyCode(), flag);
		attacking = flag;
	}
	
	public static boolean getPlaceing() {
		return placeing;
	}
	
	public static boolean getAttacking() {
		return attacking;
	}
}

package main.java.buildbot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import main.java.buildbot.BuildBotAI.ItemBringRequest;
import main.java.buildbot.config.ConfigManager;
import main.java.buildbot.source.SourceParser;
import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid = Buildbot.MODID, useMetadata = true, guiFactory = "main.java.buildbot.config.ConfigGuiFactory")
public class Buildbot {
	public static final String MODID = "buildbot";
	public static final double DEFAULT_LOOK_SPEED = 9.0;
	static final double WALK_SPEED = 4.0;
	public static final int DEFAULT_TIMEOUT = 20; // Unit is tick(0.05sec)
	public static final int DEFAULT_DELAY = 5; // Unit is tick(0.05sec)
	public static final String DEFAULT_SOURCE = "./source.struct";
	public static final Logger LOGGER = LogManager.getLogger(MODID);
	private ConfigManager configs;
	private BuildBotAI ai;
	public SourceParser parser;
	private static Buildbot instance;
	public static final SimpleNetworkWrapper packetChannel = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

	public Buildbot() {
		if (instance != null) throw new IllegalStateException();
		instance = this;
	}

	public static Buildbot getBuildbot() {
		return instance;
	}

	@EventHandler
	public void preLoad(FMLPreInitializationEvent event) {
		if (event.getSide().isClient()) {
			Configuration config = new Configuration(event.getSuggestedConfigurationFile());
			LOGGER.info(I18n.format("buildbot.config.loading", config.getConfigFile()));
			config.load();
			configs = ConfigManager.init(config);
		}
	}

	@EventHandler
	public void load(FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this); // @SubscribeEvent で登録したリスナを有効にする
		// パケットの通り道を作成
		packetChannel.registerMessage(ItemBringer.class, ItemBringRequest.class, 0, Side.SERVER);
		packetChannel.registerMessage(BuildBotAI.Responder.class, Result.class, 1, Side.CLIENT);
		ClientCommandHandler.instance.registerCommand(new CommandBuild()); // コマンドを登録
	}

	@SubscribeEvent
	public void onentityjoinworld(EntityJoinWorldEvent event) {
		if (event.getWorld().isRemote && event.getEntity() instanceof EntityPlayerSP) onlogin(event);
	}

	@EventHandler
	public void shutdown(FMLServerStoppingEvent event) {
		ai = null;
		if (event.getSide().isClient()) {
			configs.save();
			LOGGER.info(I18n.format("buildbot.config.saved"));
		}
	}

	private void onlogin(EntityJoinWorldEvent event) {
		parser = new SourceParser();
		ai = new BuildBotAI((EntityPlayerSP) event.getEntity());
	}

	@SubscribeEvent
	public void onTick(PlayerTickEvent e) {
		if (ai != null && !ai.isConstructed() && e.side.isClient()) {
			ai.update();
			if (ai.isConstructed()) Buildbot.LOGGER.info(I18n.format("buildbot.constructed"));
		}
	}

	// デバッグメッセージの整形に使用
	public static String smartString(Object obj) {
		if (obj instanceof Vec3i)
			return "(" + ((Vec3i) obj).getX() + ", " + ((Vec3i) obj).getY() + ", " + ((Vec3i) obj).getZ() + ")";
		if (obj instanceof Block) return ((Block) obj).getUnlocalizedName().substring(5); // remove 'tile.'
		if (obj instanceof Path) {
			StringBuilder str = new StringBuilder();
			str.append('[');
			str.append(((Path) obj).getPathPointFromIndex(0));
			for (int i = 1; i < ((Path) obj).getCurrentPathLength(); i++) {
				str.append("], [");
				str.append(((Path) obj).getPathPointFromIndex(i));
			}
			str.append(']');
			return str.toString();
		}
		return "";
	}

	public static BuildBotAI getAI() {
		return instance.ai;
	}

	public static class ItemBringer implements IMessageHandler<ItemBringRequest, IMessage> {
		@Override
		public IMessage onMessage(ItemBringRequest message, MessageContext ctx) {
			EntityPlayerMP player = ctx.getServerHandler().playerEntity;
			int empty = player.inventory.getFirstEmptyStack();
			if (empty >= 0) {
				player.inventory.mainInventory.set(empty, new ItemStack(message.block));
				player.inventory.pickItem(empty);
				player.inventoryContainer.detectAndSendChanges();
				player.updateHeldItem();
				LOGGER.debug(I18n.format("buildbot.itembringed", player.inventory.getCurrentItem()));
				return new Result(true);
			}
			LOGGER.warn(I18n.format("buildbot.error.noinventory", smartString(message.block), player.getName()));
			return new Result(false);
		}
	}
}

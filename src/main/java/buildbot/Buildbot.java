package main.java.buildbot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jparsec.error.ParserException;

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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(modid = Buildbot.MODID, useMetadata = true, guiFactory = "main.java.buildbot.config.ConfigGuiFactory")
public class Buildbot {
	public static final String MODID = "buildbot";
	public static final double DEFAULT_LOOK_SPEED = 9.0;
	static final double WALK_SPEED = 4.0;
	public static final int DEFAULT_TIMEOUT = 20; // Unit is tick(0.05sec)
	public static final String DEFAULT_SOURCE = "./source.struct";
	public static final Logger LOGGER = LogManager.getLogger(MODID);
	private ConfigManager configs;
	private BuildBotAI ai;
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
		MinecraftForge.EVENT_BUS.register(this);
		packetChannel.registerMessage(ItemBringer.class, ItemBringRequest.class, 0, Side.SERVER);
		packetChannel.registerMessage(BuildBotAI.Responder.class, Result.class, 1, Side.CLIENT);
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
		LOGGER.info(I18n.format("buildbot.source.loading", Paths.get(configs.getPropSource()).toAbsolutePath()));
		Set<PlaceData> places = load(Paths.get(configs.getPropSource()));
		if (places.isEmpty()) LOGGER.info(I18n.format("buildbot.source.empty"));
		ai = new BuildBotAI((EntityPlayerSP) event.getEntity(), places);
	}

	@SideOnly(Side.CLIENT)
	private HashSet<PlaceData> load(java.nio.file.Path path) {
		try {
			return Files.lines(path).collect(() -> new HashSet<>(), SourceParser::parseLine, (q, r) -> q.addAll(r));
		} catch (NoSuchFileException e) {
			LOGGER.error(I18n.format("buildbot.source.notfound"));
		} catch (IOException e) {
			LOGGER.catching(e);
			LOGGER.error(I18n.format("buildbot.source.loadfailed"));
		} catch (ParserException e) {
			if (configs.getPropDebug()) LOGGER.catching(e);
			LOGGER.error(I18n.format("buildbot.source.invaild"));
		}
		throw new InternalError();
	}

	@SubscribeEvent
	public void onTick(PlayerTickEvent e) {
		if (ai != null && !ai.isConstructed() && configs.getPropEnable() && e.side.isClient()) {
			ai.update();
			if (ai.isConstructed()) Buildbot.LOGGER.info(I18n.format("buildbot.constructed"));
		}
	}

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
				if (!player.capabilities.isCreativeMode) player.updateHeldItem();
				LOGGER.debug(I18n.format("buildbot.itembringed", player.inventory.getCurrentItem()));
				return new Result(true);
			}
			LOGGER.warn(I18n.format("buildbot.error.noinventory", smartString(message.block), player.getName()));
			return new Result(false);
		}
	}
}

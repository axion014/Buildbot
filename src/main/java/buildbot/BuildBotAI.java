package main.java.buildbot;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import io.netty.buffer.ByteBuf;
import main.java.buildbot.config.ConfigManager;
import main.java.buildbot.pathfinding.PathNavigatePlayer;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class BuildBotAI {

	private EntityPlayerSP player;
	private PathNavigatePlayer navigate;
	private boolean bringingItem;
	private Minecraft minecraft = Minecraft.getMinecraft();
	private BlockData attackingBlock;
	private LookTester lookTester;
	private Set<PlaceData> places;
	private PlaceData place;
	private String lastError;
	private int placeDelay;
	private int timeOut;
	private boolean stopping;
	private boolean constructed;

	public BuildBotAI(EntityPlayerSP player, Set<PlaceData> places) {
		this.player = player;
		this.places = places;
		navigate = new PathNavigatePlayer(player, player.world);
		lookTester = new LookTester(player);
		placeDelay = 0;
	}

	public BuildBotAI(EntityPlayerSP player) {
		this(player, new HashSet<>());
	}

	public void update() {
		boolean flag = false;
		while (place == null || place.block == player.world.getBlockState(place.pos).getBlock()) {
			KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindSneak.getKeyCode(), false);
			if (nextBlock()) {
				flag = true;
				placeDelay = ConfigManager.get().getPropDelay();
			} else {
				constructed = true;
				return;
			}
		}
		if (placeDelay > 0) {
			placeDelay--;
			return;
		}
		if (flag) Buildbot.LOGGER.debug(I18n.format("buildbot.blockplaceready", Buildbot.smartString(place.block),
			Buildbot.smartString(place.pos)));
		if (timeOut != -1) timeOut--;
		if (timeOut == 0) {
			timeOut = -1;
			resetClickState();
			Buildbot.LOGGER.warn(I18n.format("buildbot.error.placetimeout"));
			stopping = true;
			return;
		}
		if (stopping) return;
		PlaceTestResult result = lookTester.getCanPlaceBlockAt(place);
		Vec3d targetCenter = new Vec3d(place.pos).addVector(0.5, 0.5, 0.5);
		float lookspeed = (float) ConfigManager.get().getPropLookspeed();
		if (result.issuccess) {
			navigate.clearPathEntity();
			KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindSneak.getKeyCode(), true);
			minecraft.player.movementInput.sneak = true;
			Vec3d placePos = targetCenter.add(new Vec3d(result.placableface.getDirectionVec()).scale(0.5));
			if (navigate.getLookHelper().getLookPosition() == null
					|| !navigate.getLookHelper().getLookPosition().equals(placePos))
				navigate.getLookHelper().setLookPosition(placePos, lookspeed, lookspeed);
			if (!navigate.getLookHelper().getIsLooking()) {
				if (trypick(player, place.block)) {
					if (minecraft.objectMouseOver.typeOfHit == Type.BLOCK && minecraft.objectMouseOver.getBlockPos()
							.offset(minecraft.objectMouseOver.sideHit).equals(place.pos)) {
						if (!ClickHelper.getPlaceing()) {
							Buildbot.LOGGER.info(I18n.format("buildbot.placeingblock",
								Buildbot.smartString(((ItemBlock) player.inventory.getCurrentItem().getItem()).block),
								Buildbot.smartString(place.pos)));
							placeBlock();
						}
					} else {
						Buildbot.LOGGER.debug("place failed, retrying...");
						navigate.getLookHelper().relook();
					}
				} else synchronized (this) {
					if (!bringingItem) {
						if (player.isCreative()) {
							bringingItem = true;
							Buildbot.packetChannel.sendToServer(new ItemBringRequest(place.block));
							lastError = null;
						} else {
							if (checkAndSetLastError("no block")) return;
							player.sendMessage(new TextComponentTranslation("buildbot.error.noblock",
								Buildbot.smartString(place.block), Buildbot.smartString(place.pos), player.getName()));
							stopping = true;
						}
					}
				}
			}
		} else {
			switch (result.cause) {
				case NO_BASE_BLOCK:
					if (place.errored) {
						if (checkAndSetLastError(FailCauses.NO_BASE_BLOCK.name())) return;
						player.sendMessage(new TextComponentTranslation("buildbot.error.nobaseblock", Buildbot.smartString(place.pos),
							player.getName()));
					} else rotatePlaceOrdinal();
					break;
				case TOO_NEAR:
					navigate.getLookHelper().lookIfNotAlreadyLooked(targetCenter.addVector(0, 0.5, 0), lookspeed,
						lookspeed);
					if (!navigate.getLookHelper().getIsLooking()) navigate.getJumpHelper().setJumping();
					if (!checkAndSetLastError("buildbot.info.tooclose"))
						Buildbot.LOGGER.debug(player.getName() + " got too close");
					break;
				case OCCUPIED:
					if (lookTester.canLookAt(place.pos)) {
						navigate.getLookHelper().lookIfNotAlreadyLooked(targetCenter, lookspeed, lookspeed);
						if (!(ClickHelper.getAttacking() || navigate.getLookHelper().getIsLooking())) {
							Buildbot.LOGGER.info(I18n.format("buildbot.collectingblock",
								Buildbot.smartString(player.world.getBlockState(place.pos).getBlock()),
								Buildbot.smartString(place.pos)));
							attackTo(new BlockData(place.pos, player.world.getBlockState(place.pos).getBlock()));
						}
						break;
					}
				default:
					if (navigate.tryMoveToXYZ(targetCenter.xCoord, targetCenter.yCoord, targetCenter.zCoord,
						Buildbot.WALK_SPEED)) {
						KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindSprint.getKeyCode(), true);
						break;
					}
					if (place.errored) {
						if (checkAndSetLastError("can't go to next position")) return;
						player.sendMessage(new TextComponentTranslation("buildbot.error.nopath", Buildbot.smartString(place.pos), player.getName()));
					} else rotatePlaceOrdinal();
			}
		}
		navigate.update();
	}

	private boolean trypick(EntityPlayer player, Block placeblock) {
		if (player.inventory.getCurrentItem().getItem() instanceof ItemBlock
				&& placeblock == ((ItemBlock) player.inventory.getCurrentItem().getItem()).block)
			return true;
		for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
			if (player.inventory.mainInventory.get(i).getItem() instanceof ItemBlock
					&& placeblock == ((ItemBlock) player.inventory.mainInventory.get(i).getItem()).block) {
				player.inventory.pickItem(i);
				player.inventoryContainer.detectAndSendChanges();
				minecraft.playerController.sendSlotPacket(player.getHeldItem(EnumHand.MAIN_HAND),
					36 + player.inventory.currentItem);
				return true;
			}
		}
		return false;
	}

	private void placeBlock() {
		ClickHelper.setPlaceing(true);
		timeOut = ConfigManager.get().getPropTimeout();
		navigate.getLookHelper().resetLook();
	}

	@Deprecated
	private void attack() {
		ClickHelper.setAttacking(true);
		timeOut = ConfigManager.get().getPropTimeout();
		navigate.getLookHelper().resetLook();
	}

	private void attackTo(BlockData target) {
		attack();
		attackingBlock = target;
	}

	private void resetClickState() {
		if (ClickHelper.getPlaceing()) ClickHelper.setPlaceing(false);
		if (ClickHelper.getAttacking() && (attackingBlock != null
			? minecraft.world.getBlockState(attackingBlock.pos).getBlock() != attackingBlock.block
			: minecraft.world.isAirBlock(place.pos))) {
			ClickHelper.setAttacking(false);
			attackingBlock = null;
			Buildbot.LOGGER.debug(I18n.format("buildbot.collected"));
		}
		KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindSneak.getKeyCode(), false);
	}

	private boolean checkAndSetLastError(String error) {
		if (Objects.equals(lastError, error)) return true;
		lastError = error;
		return false;
	}

	private boolean nextBlock() {
		resetClickState();
		if (places.isEmpty()) return false;
		double highest = Double.NEGATIVE_INFINITY;
		for (PlaceData place : places) {
			double priority = place.priority - MathHelper.sqrt(minecraft.player.getDistanceSq(place.pos))
					- place.pos.getY() * 2;
			if (highest < priority) {
				highest = priority;
				this.place = place;
			}
		}
		places.remove(place);
		lastError = null;
		timeOut = -1;
		stopping = false;
		return true;
	}

	private void rotatePlaceOrdinal() {
		Buildbot.LOGGER.debug(
			I18n.format("buildbot.rotateordinal", Buildbot.smartString(place.pos), Buildbot.smartString(place.block)));
		place.priority -= 5.0;
		place.errored = true;
		places.add(place);
		nextBlock();
		Buildbot.LOGGER.debug(I18n.format("buildbot.blockplaceready", Buildbot.smartString(place.block),
			Buildbot.smartString(place.pos)));
	}

	public void setPlaceData(Set<PlaceData> places) {
		this.places = places;
		place = null;
		constructed = false;
	}
	
	public void addPlaceData(Set<PlaceData> places) {
		this.places.addAll(places);
		constructed = false;
	}

	public boolean isConstructed() {
		return constructed;
	}

	public static class ItemBringRequest implements IMessage {
		public Block block;

		public ItemBringRequest() {}

		public ItemBringRequest(Block block) {
			this.block = block;
		}

		@Override
		public void fromBytes(ByteBuf buf) {
			block = Block.getBlockById(buf.readInt());
		}

		@Override
		public void toBytes(ByteBuf buf) {
			buf.writeInt(Block.getIdFromBlock(block));
		}
	}

	public static class Responder implements IMessageHandler<Result, IMessage> {
		@Override
		public IMessage onMessage(Result message, MessageContext ctx) {
			if (message.issuccess) synchronized (Buildbot.getAI()) {
				Buildbot.getAI().bringingItem = false;
			}
			return null;
		}
	}
}

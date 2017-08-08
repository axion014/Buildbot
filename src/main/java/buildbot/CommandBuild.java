package main.java.buildbot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.jparsec.error.ParserException;

import main.java.buildbot.source.SourceParser;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

public class CommandBuild extends CommandBase {
	
	@Override
	public int getRequiredPermissionLevel() {
		return 0;
	}

	@Override
	public String getName() {
		return "build";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		sender.sendMessage(new TextComponentTranslation("buildbot.command.build.usage"));
		return I18n.format("buildbot.command.build.workdiris", Paths.get(".").toAbsolutePath());
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		String arg = String.join(" ", args);
		if (arg.equals("")) {
			sender.sendMessage(new TextComponentString(getUsage(sender)));
			return;
		}
		if (args.length == 1 && args[0].equals("stop")) {
			Buildbot.getAI().setPlaceData(new HashSet<>());
			return;
		}
		try {
			byte[] bytes = Files.readAllBytes(Paths.get(arg));
			Buildbot.LOGGER.info(I18n.format("buildbot.source.loading", Paths.get(arg).toAbsolutePath()));
			Set<PlaceData> places = new SourceParser().parse(new String(bytes, StandardCharsets.UTF_8));
			if (places.isEmpty()) Buildbot.LOGGER.info(I18n.format("buildbot.source.empty"));
			else {
				Buildbot.getAI().addPlaceData(places);
				Buildbot.LOGGER.info(I18n.format("buildbot.command.build.success"));
			}
		} catch (NoSuchFileException e) {
			try {
				Buildbot.getAI().addPlaceData(Buildbot.getBuildbot().parser.parseLine(arg));
				Buildbot.LOGGER.info(I18n.format("buildbot.command.build.success"));
			} catch (ParserException ex) {
				throw new CommandException("buildbot.command.build.invaild", ex);
			}
		} catch (IOException e) {
			Buildbot.LOGGER.catching(e);
			Buildbot.LOGGER.error(I18n.format("buildbot.source.loadfailed"));
		} catch (ParserException e) {
			Buildbot.LOGGER.error(I18n.format("buildbot.source.invaild"));
			Buildbot.LOGGER.error(e.getMessage());
		}
	}

}

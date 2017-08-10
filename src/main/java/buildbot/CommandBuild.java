package main.java.buildbot;

import java.nio.file.Paths;
import java.util.HashSet;

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
			sender.sendMessage(new TextComponentTranslation("buildbot.command.build.stop"));
			Buildbot.getAI().setPlaceData(new HashSet<>());
			return;
		}
		try {
			Buildbot.getAI().addPlaceData(SourceParser.parse(arg));
			sender.sendMessage(new TextComponentTranslation("buildbot.command.build.success"));
		} catch (ParserException ex) {
			Buildbot.LOGGER.info(ex);
			throw new CommandException("buildbot.command.build.invaild");
		}
	}

}

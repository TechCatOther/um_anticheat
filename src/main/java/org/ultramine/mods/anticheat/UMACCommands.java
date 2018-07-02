package org.ultramine.mods.anticheat;

import org.ultramine.commands.Command;
import org.ultramine.commands.CommandContext;

public class UMACCommands
{
	@Command(
			name = "anticheat",
			group = "anticheat",
			aliases = {"umac"},
			syntax = {"[reload]"}
	)
	public static void anticheat(CommandContext ctx)
	{
		UMAntiCheat.instance().reload();
		ctx.sendMessage("OK");
	}
}

package to.rtc.cli.migrate;

import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;

public class MigrateToOptions implements IOptionSource {
	public static final IOptionKey OPT_SRC_WS = new OptionKey("source-workspace-name"); //$NON-NLS-1$
	public static final IOptionKey OPT_DEST_WS = new OptionKey("destination-workspace-name"); //$NON-NLS-1$

	public static final IOptionKey OPT_RTC_CONNECTION_TIMEOUT = new OptionKey("timeout");
	public static final IOptionKey OPT_RTC_LIST_TAGS_ONLY = new OptionKey("listTagsOnly");
	public static final IOptionKey OPT_RTC_IS_UPDATE_MIGRATION = new OptionKey("updateMigration");
	public static final IOptionKey OPT_RTC_LOAD_ARGS = new OptionKey("loadArgs");

	@Override
	public Options getOptions() throws ConflictingOptionException {
		Options options = new Options(false);

		SubcommandUtil.addRepoLocationToOptions(options);
		options.addOption(new PositionalOptionDefinition(OPT_SRC_WS, "source-workspace-name", 1, 1), //$NON-NLS-1$
				"name of the pre configured source RTC workspace that could follow the stream to migrate.");
		options.addOption(new PositionalOptionDefinition(OPT_DEST_WS, "destination-workspace-name", 1, 1), //$NON-NLS-1$
				"name of the pre configured RTC workspace that holds the current state of the migration and that follows the source-workspace-name.");

		options.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP);
		options.addOption(new NamedOptionDefinition(OPT_RTC_CONNECTION_TIMEOUT, "t", "timeout", 1),
				"Timeout in seconds, default is 900");
		options.addOption(new NamedOptionDefinition(OPT_RTC_LIST_TAGS_ONLY, "L", "list-tags-only", 0),
				"List only all tags that would be migrated but do not migrate them.");
		options.addOption(new NamedOptionDefinition(OPT_RTC_IS_UPDATE_MIGRATION, "U", "update", 0),
				"Update the content of an already migrated workspace.");
		options.addOption(new NamedOptionDefinition(OPT_RTC_LOAD_ARGS, "A", "args", 1),
				"Provide any extra load arguments");
		return options;
	}
}

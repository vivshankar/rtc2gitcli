package to.rtc.cli.migrate;

import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.ibm.team.filesystem.cli.core.Constants;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;
import com.ibm.team.rtc.cli.infrastructure.internal.core.CLIClientException;

import to.rtc.cli.migrate.command.AcceptCommandDelegate;
import to.rtc.cli.migrate.command.LoadCommandDelegate;
import to.rtc.cli.migrate.util.Files;

public class RtcMigrator {

	/**
	*
	*/
	private static final int ACCEPTS_BEFORE_LOCAL_HISTORY_CLEAN = 1000;
	protected final IChangeLogOutput output;
	private final IScmClientConfiguration config;
	private final String workspace;
	private final Migrator migrator;
	private final Set<String> initiallyLoadedComponents;
	private File sandboxDirectory;
	private String loadArgs;

	public RtcMigrator(IChangeLogOutput output, IScmClientConfiguration config, String workspace, Migrator migrator,
			File sandboxDirectory, Collection<String> initiallyLoadedComponents, boolean isUpdateMigration,
			String loadArgs) {
		this.output = output;
		this.config = config;
		this.workspace = workspace;
		this.migrator = migrator;
		this.sandboxDirectory = sandboxDirectory;
		this.initiallyLoadedComponents = new HashSet<String>(initiallyLoadedComponents);
		this.loadArgs = loadArgs;
	}

	public void migrateTag(RtcTag tag) throws CLIClientException {
		List<RtcChangeSet> changeSets = tag.getOrderedChangeSets();
		int changeSetCounter = 0;
		int numberOfChangesets = changeSets.size();
		String tagName = tag.getName();
		for (RtcChangeSet changeSet : changeSets) {
			try {
				long acceptDuration = accept(changeSet);
				long commitDuration = commit(changeSet);
				changeSetCounter++;
				output.writeLine("Migrated [" + tagName + "] [" + changeSetCounter + "]/[" + numberOfChangesets
						+ "] changesets. Accept took " + acceptDuration + "ms commit took " + commitDuration + "ms");
				if (migrator.needsIntermediateCleanup()) {
					intermediateCleanup();
				}
				if (changeSetCounter % ACCEPTS_BEFORE_LOCAL_HISTORY_CLEAN == 0) {
					cleanLocalHistory();
				}
			} catch (CLIClientException clie) {
				output.writeLine("Changeset details:");
				output.writeLine("  Tag original name       : " + tag.getOriginalName());
				output.writeLine("  Tag creation date       : " + new Date(tag.getCreationDate()));
				output.writeLine("  Changeset comment       : " + changeSet.getComment());
				output.writeLine("  Changeset creator       : " + changeSet.getCreatorName());
				output.writeLine("  Changeset creation date : " + new Date(changeSet.getCreationDate()));
				output.writeLine("  Changeset component     : " + changeSet.getComponent());
				output.writeLine("  Changeset UUID          : " + changeSet.getUuid());
				throw clie;
			}
		}
		cleanLocalHistory();
		if (tag.doCreateTag()) {
			migrator.createTag(tag);
		}
	}

	void intermediateCleanup() {
		long startCleanup = System.currentTimeMillis();
		migrator.intermediateCleanup();
		output.writeLine("Intermediate cleanup had ["
				+ (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startCleanup)) + "]sec");
	}

	long commit(RtcChangeSet changeSet) {
		long startCommit = System.currentTimeMillis();
		migrator.commitChanges(changeSet);
		long commitDuration = System.currentTimeMillis() - startCommit;
		return commitDuration;
	}

	long accept(RtcChangeSet changeSet) throws CLIClientException {
		long startAccept = System.currentTimeMillis();
		acceptAndLoadChangeSet(changeSet);
		handleInitialLoad(changeSet, this.loadArgs);
		long acceptDuration = System.currentTimeMillis() - startAccept;
		return acceptDuration;
	}

	private void cleanLocalHistory() {
		File localHistoryDirectory = new File(sandboxDirectory,
				".metadata/.plugins/org.eclipse.core.resources/.history");
		if (localHistoryDirectory.exists() && localHistoryDirectory.isDirectory()) {
			long start = System.currentTimeMillis();
			Files.delete(localHistoryDirectory);
			output.writeLine("Cleanup of local history had ["
					+ (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start)) + "]sec");
		}
	}

	private void acceptAndLoadChangeSet(RtcChangeSet changeSet) throws CLIClientException {
		output.setIndent(2);
		int result = new AcceptCommandDelegate(config, output, workspace, changeSet.getUuid(), false, false).run();
		switch (result) {
		case Constants.STATUS_OUT_OF_SYNC:
			output.writeLine("Try loading of workspace again with force option");
			result = new LoadCommandDelegate(config, output, workspace, null, true, loadArgs).run();
			break;
		case Constants.STATUS_GAP:
			output.writeLine("Retry accepting with --accept-missing-changesets");
			result = new AcceptCommandDelegate(config, output, workspace, changeSet.getUuid(), false, true).run();
			if (Constants.STATUS_GAP == result || Constants.STATUS_OUT_OF_SYNC == result) {
				throw new CLIClientException("There was a PROBLEM in accepting that we cannot solve.");
			}
			break;
		default:
			break;
		}
	}

	private void handleInitialLoad(RtcChangeSet changeSet, String loadArgs) {
		if (!initiallyLoadedComponents.contains(changeSet.getComponent())) {
			try {
				new LoadCommandDelegate(config, output, workspace, changeSet.getComponent(), false, loadArgs).run();
				initiallyLoadedComponents.add(changeSet.getComponent());
			} catch (CLIClientException e) {
				throw new RuntimeException("Not a valid sandbox. Please run [scm load " + workspace
						+ "] before [scm migrate-to-git] command");
			}
		}
	}

}

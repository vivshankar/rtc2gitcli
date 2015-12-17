package to.rtc.cli.migrate.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import to.rtc.cli.migrate.ChangeSet;
import to.rtc.cli.migrate.ChangeSet.WorkItem;
import to.rtc.cli.migrate.Tag;
import to.rtc.cli.migrate.util.Files;

/**
 * Tests the {@link GitMigrator} implementation.
 *
 * @author patrick.reinhart
 */
public class GitMigratorTest {
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private Charset cs;
	private GitMigrator migrator;
	private Git git;
	private Properties props;
	private File basedir;

	@Before
	public void setUo() {
		cs = Charset.forName("UTF-8");
		migrator = new GitMigrator();
		props = new Properties();
		basedir = tempFolder.getRoot();
	}

	@After
	public void tearDown() {
		migrator.close();
		if (git != null) {
			git.close();
		}
	}

	@Test
	public void testClose() {
		migrator.close();
	}

	@Test
	public void testInit_noGitRepoAvailableNoGitIgnore() throws Exception {
		props.setProperty("user.email", "john.doe@somewhere.com");
		props.setProperty("user.name", "John Doe");

		assertFalse(new File(basedir, ".gitattributes").exists());

		migrator.init(basedir, props);

		checkGit("John Doe", "john.doe@somewhere.com", "Initial commit",
				new File(basedir, ".gitignore"),
				GitMigrator.ROOT_IGNORED_ENTRIES);
		assertFalse(new File(basedir, ".gitattributes").exists());
	}

	@Test
	public void testInit_noGitRepoAvailableWithGitIgnore() throws Exception {
		Files.writeLines(new File(basedir, ".gitignore"),
				Arrays.asList("/.jazz5", "/bin/", "/.jazzShed"), cs, false);

		migrator.init(basedir, props);

		checkGit("RTC 2 git", "rtc2git@rtc.to", "Initial commit", new File(
				basedir, ".gitignore"), Arrays.asList("/.jazz5", "/bin/",
				"/.jazzShed", "/.metadata"));
	}

	@Test
	public void testInit_Gitattributes() throws IOException {
		props.setProperty("gitattributes", "* text=auto");

		migrator.init(basedir, props);

		File gitattributes = new File(basedir, ".gitattributes");
		assertTrue(gitattributes.exists() && gitattributes.isFile());
		List<String> expectedLines = new ArrayList<String>();
		expectedLines.add("* text=auto");
		assertEquals(expectedLines, Files.readLines(gitattributes, cs));
	}

	@Test
	public void testInit_GitRepoAvailable() throws Exception {
		git = Git.init().setDirectory(basedir).call();

		migrator.init(basedir, props);

		checkGit("RTC 2 git", "rtc2git@rtc.to", "Initial commit", new File(
				basedir, ".gitignore"), GitMigrator.ROOT_IGNORED_ENTRIES);
	}

	@Test
	public void testGetCommitMessage() {
		migrator.init(basedir, props);

		assertEquals("gugus gaga", migrator.getCommitMessage("gugus", "gaga"));
	}

	@Test
	public void testGetCommitMessage_withCustomFormat() {
		props.setProperty("commit.message.format", "%1s%n%n%2s");
		migrator.init(basedir, props);
		String lf = System.getProperty("line.separator");

		assertEquals("gug%us" + lf + lf + "ga%ga",
				migrator.getCommitMessage("gug%us", "ga%ga"));
	}

	@Test
	public void testGetWorkItemNumbers_noWorkItems() {
		assertEquals("",
				migrator.getWorkItemNumbers(Collections.<WorkItem> emptyList()));
	}

	@Test
	public void testGetWorkItemNumbers_singleWorkItem() {
		migrator.init(basedir, props);
		List<WorkItem> items = new ArrayList<WorkItem>();
		items.add(TestWorkItem.INSTANCE1);
		assertEquals("4711", migrator.getWorkItemNumbers(items));
	}

	@Test
	public void testGetWorkItemNumbers_singleWorkItem_customFormat() {
		props.setProperty("rtc.workitem.number.format", "RTC-%s");
		migrator.init(basedir, props);

		List<WorkItem> items = new ArrayList<WorkItem>();
		items.add(TestWorkItem.INSTANCE1);
		assertEquals("RTC-4711", migrator.getWorkItemNumbers(items));
	}

	@Test
	public void testGetWorkItemNumbers_multipleWorkItems() {
		props.setProperty("rtc.workitem.number.format", "RTC-%s");
		migrator.init(basedir, props);

		List<WorkItem> items = new ArrayList<WorkItem>();
		items.add(TestWorkItem.INSTANCE1);
		items.add(TestWorkItem.INSTANCE2);
		assertEquals("RTC-4711 RTC-4712", migrator.getWorkItemNumbers(items));
	}

	@Test
	public void testCommitChanges() throws Exception {
		migrator.init(basedir, props);

		File testFile = new File(basedir, "somefile");
		Files.writeLines(testFile, Collections.singletonList("somevalue"), cs,
				false);

		migrator.commitChanges(TestChangeSet.INSTANCE);

		checkGit("Heiri Mueller", "heiri.mueller@irgendwo.ch",
				"4711 the checkin comment", testFile,
				Collections.singletonList("somevalue"));
	}

	@Test
	public void testCommitChanges_noWorkItem() throws Exception {
		migrator.init(basedir, props);

		File testFile = new File(basedir, "somefile");
		Files.writeLines(testFile, Collections.singletonList("somevalue"), cs,
				false);

		migrator.commitChanges(TestChangeSet.NO_WORKITEM_INSTANCE);

		checkGit("Heiri Mueller", "heiri.mueller@irgendwo.ch",
				"the checkin comment", testFile,
				Collections.singletonList("somevalue"));
	}

	@Test
	public void testGetGitattributeLines() throws Exception {
		props.setProperty("gitattributes",
				" # handle text files; * text=auto; *.sql text");
		setMigratorProperties(props);
		List<String> lines = migrator.getGitattributeLines();
		assertNotNull(lines);
		assertEquals(3, lines.size());
		assertEquals("# handle text files", lines.get(0));
		assertEquals("* text=auto", lines.get(1));
		assertEquals("*.sql text", lines.get(2));
	}

	@Test
	public void testAddMissing() {
		List<String> existing = new ArrayList<String>();
		existing.add("0");
		existing.add("3");
		List<String> adding = new ArrayList<String>();
		adding.add("0");
		adding.add("1");
		adding.add("2");
		adding.add("4");
		List<String> expectedLines = new ArrayList<String>();
		expectedLines.add("0");
		expectedLines.add("3");
		expectedLines.add("1");
		expectedLines.add("2");
		expectedLines.add("4");
		migrator.addMissing(existing, adding);
		assertEquals(expectedLines, existing);
	}

	@Test
	public void testAddUpdateGitignoreIfJazzignoreAddedOrChanged()
			throws Exception {
		migrator.init(basedir, props);
		File jazzignore = new File(basedir, ".jazzignore");
		File gitignore = new File(basedir, ".gitignore");

		Files.writeLines(jazzignore, Arrays.asList("core.ignore = {*.suo}",
				"core.ignore.recursive = {*.class}"), cs, false);

		migrator.commitChanges(TestChangeSet.INSTANCE);

		checkGit("Heiri Mueller", "heiri.mueller@irgendwo.ch",
				"4711 the checkin comment", gitignore,
				Arrays.asList("/*.suo", "*.class"));
	}

	@Test
	public void testAddUpdateGitignoreIfJazzignoreAddedOrChangedInSubdirectory()
			throws Exception {
		migrator.init(basedir, props);
		File subdir = tempFolder.newFolder("subdir");
		File jazzignore = new File(subdir, ".jazzignore");
		File gitignore = new File(subdir, ".gitignore");

		Files.writeLines(jazzignore, Arrays.asList("core.ignore = {*.suo}",
				"core.ignore.recursive = {*.class}"), cs, false);

		migrator.commitChanges(TestChangeSet.INSTANCE);

		checkGit("Heiri Mueller", "heiri.mueller@irgendwo.ch",
				"4711 the checkin comment", gitignore,
				Arrays.asList("/*.suo", "*.class"));
	}

	@Test
	public void testRemovedGitignoreIfJazzignoreRemoved() throws Exception {
		migrator.init(basedir, props);
		File jazzignore = new File(basedir, ".jazzignore");
		File gitignore = new File(basedir, ".gitignore");

		Files.writeLines(jazzignore, Arrays.asList("core.ignore = {*.suo}",
				"core.ignore.recursive = {*.class}"), cs, false);
		migrator.commitChanges(TestChangeSet.INSTANCE);

		assertTrue(jazzignore.delete());

		migrator.commitChanges(TestChangeSet.INSTANCE);

		assertFalse(gitignore.exists());
	}

	@Test
	public void testRestoreGitignoreIfJazzignoreNotRemoved() throws Exception {
		migrator.init(basedir, props);
		File jazzignore = new File(basedir, ".jazzignore");
		File gitignore = new File(basedir, ".gitignore");

		Files.writeLines(jazzignore, Arrays.asList("core.ignore = {*.suo}",
				"core.ignore.recursive = {*.class}"), cs, false);
		migrator.commitChanges(TestChangeSet.INSTANCE);

		assertTrue(gitignore.delete());

		migrator.commitChanges(TestChangeSet.INSTANCE);

		assertTrue(gitignore.exists());
	}

	@Test
	public void testRestoreGitignoreIfJazzignoreNotRemovedInSubdirectory()
			throws Exception {
		migrator.init(basedir, props);
		File subdir = tempFolder.newFolder("subdir");
		File jazzignore = new File(subdir, ".jazzignore");
		File gitignore = new File(subdir, ".gitignore");

		Files.writeLines(jazzignore, Arrays.asList("core.ignore = {*.suo}",
				"core.ignore.recursive = {*.class}"), cs, false);
		migrator.commitChanges(TestChangeSet.INSTANCE);

		assertTrue(gitignore.delete());

		migrator.commitChanges(TestChangeSet.INSTANCE);

		assertTrue(gitignore.exists());
	}

	@Test
	public void testCreateTag() throws Exception {
		migrator.init(basedir, props);

		migrator.createTag(TestTag.INSTANCE);

		git = Git.open(basedir);
		List<Ref> tags = git.tagList().call();
		assertEquals(1, tags.size());
		Ref ref = tags.get(0);
		assertEquals("refs/tags/myTag", ref.getName());
	}

	//
	// helper stuff
	//

	private void setMigratorProperties(Properties properties) throws Exception {
		Field field = migrator.getClass().getDeclaredField("properties");
		assertNotNull("field not found", field);
		field.setAccessible(true);
		field.set(migrator, properties);
	}

	private void checkGit(String userName, String userEmail, String comment,
			File checkedFile, List<String> checkedContent) throws Exception {
		assertEquals(checkedContent, Files.readLines(checkedFile, cs));
		git = Git.open(basedir);
		Status status = git.status().call();
		assertTrue(status.getUncommittedChanges().isEmpty());
		assertTrue(status.getUntracked().isEmpty());
		Iterator<RevCommit> log = git.log().call().iterator();
		RevCommit revCommit = log.next();
		assertEquals(userEmail, revCommit.getAuthorIdent().getEmailAddress());
		assertEquals(userName, revCommit.getAuthorIdent().getName());
		assertEquals(comment, revCommit.getFullMessage());
	}

	private enum TestChangeSet implements ChangeSet {
		INSTANCE, NO_WORKITEM_INSTANCE {
			@Override
			public List<WorkItem> getWorkItems() {
				return Collections.emptyList();
			}
		};

		@Override
		public String getComment() {
			return "the checkin comment";
		}

		@Override
		public String getCreatorName() {
			return "Heiri Mueller";
		}

		@Override
		public String getEmailAddress() {
			return "heiri.mueller@irgendwo.ch";
		}

		@Override
		public long getCreationDate() {
			return 0;
		}

		@Override
		public List<WorkItem> getWorkItems() {
			List<WorkItem> items = new ArrayList<WorkItem>();
			items.add(TestWorkItem.INSTANCE1);
			return items;
		}
	}

	private enum TestWorkItem implements WorkItem {
		INSTANCE1 {
			@Override
			public long getNumber() {
				return 4711;
			}

			@Override
			public String getText() {
				return "The one and only";
			}
		},
		INSTANCE2 {
			@Override
			public long getNumber() {
				return 4712;
			}

			@Override
			public String getText() {
				return "The even more and only";
			}
		};
	}

	private enum TestTag implements Tag {
		INSTANCE;

		@Override
		public String getName() {
			return "myTag";
		}

		@Override
		public long getCreationDate() {
			return 0;
		}
	}
}
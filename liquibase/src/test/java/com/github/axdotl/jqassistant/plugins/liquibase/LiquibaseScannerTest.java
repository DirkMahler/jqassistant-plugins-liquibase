package com.github.axdotl.jqassistant.plugins.liquibase;

import java.io.File;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.buschmais.jqassistant.core.plugin.api.PluginRepositoryException;
import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.impl.ScannerImpl;
import com.buschmais.jqassistant.core.store.api.Store;
import com.buschmais.jqassistant.plugin.common.test.AbstractPluginIT;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.ChangeLogDescriptor;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.ChangeSetDescriptor;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.IncludeDescriptor;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.LiquibaseDescriptor;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.refactoring.AddColumnDescriptor;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.refactoring.AddForeignKeyDescriptor;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.refactoring.AddNotNullConstraintDescriptor;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.refactoring.AddPrimaryKeyDescriptor;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.refactoring.AddUniqueConstraintDescriptor;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.refactoring.CreateSequenceDescriptor;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.refactoring.CreateTableDescriptor;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.refactoring.DropColumnDescriptor;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.refactoring.DropConstraintDescriptor;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.refactoring.DropTableDescriptor;
import com.github.axdotl.jqassistant.plugins.liquibase.descriptor.refactoring.SqlDescriptor;

/**
 * Test class for the {@link LiquibaseScannerPlugin}.
 * 
 * @author Axel Koehler
 */
public class LiquibaseScannerTest extends AbstractPluginIT {

    private final static String DIR_NON_CHANGELOG = "nonChangeLog";
    private final static String DIR_CHANGELOG = "changeLog";
    private final static String FILE_MASTER = "master.xml";
    private final static String FILE_VALID_CHANGELOG = "validChangeLog.xml";
    private final static String FILE_INCLUDED_BY_MASTER = "includedByMaster.xml";
    private final static String FILE_ALL_KOWN_REFACTORINGS = "allKnownRefactorings.xml";
    private final static String SET_ATTR_ID_COMPLETE = "complete";

    private Scanner scanner;
    private File testClassesDir;
    private Store spyStore;

    /**
     * Reset scanner and get classes directory.
     */
    @Before
    public void prepare() {

        scanner = getScanner();
        testClassesDir = getClassesDirectory(LiquibaseScannerTest.class);
    }

    /**
     * Test whether non-changelog-files will be rejected (not accepted).
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void acceptOnlyDatabaseChangeLogFiles() {

        File nonChangeLogDir = new File(testClassesDir, DIR_NON_CHANGELOG);
        File[] files = nonChangeLogDir.listFiles();
        store.beginTransaction();

        for (File f : files) {
            scanner.scan(f, "/" + f.getName(), null);
        }
        store.commitTransaction();

        // ////////////////////////////////////////////////////////////////////
        // Verify
        ArgumentCaptor<Class> capturedDescriptors = ArgumentCaptor.forClass(Class.class);
        Mockito.verify(spyStore, Mockito.atLeastOnce()).create(capturedDescriptors.capture());

        for (Class type : capturedDescriptors.getAllValues()) {
            if (type.isAssignableFrom(LiquibaseDescriptor.class)) {
                Assert.fail("Invalid files must not be scanned.");
            }
        }
    }

    /**
     * Test whether includes will be detected.
     */
    @Test
    public void detectIncludes() {

        File masterFile = new File(testClassesDir, DIR_CHANGELOG + "/" + FILE_MASTER);
        store.beginTransaction();

        ChangeLogDescriptor changeLogDescriptor = scanner.scan(masterFile, "/" + DIR_CHANGELOG + "/" + FILE_MASTER, null);
        // ////////////////////////////////////////////////////////////////////
        // Verify
        Mockito.verify(spyStore, Mockito.times(2)).create(IncludeDescriptor.class);
        List<IncludeDescriptor> includes = changeLogDescriptor.getIncludes();
        Assert.assertEquals("Includes expected.", 2, includes.size());

        store.commitTransaction();
    }

    /**
     * Test whether the available sub-tags <i>comment, preConditions, &lt;refactorings&gt;, rollback</i> will be applied.
     */
    @Test
    public void applyChangeSetSubTags() {

        File changeLogFile = new File(testClassesDir, DIR_CHANGELOG + "/" + FILE_VALID_CHANGELOG);
        store.beginTransaction();

        ChangeLogDescriptor changeLogDescriptor = scanner.scan(changeLogFile, "/" + DIR_CHANGELOG + "/" + FILE_VALID_CHANGELOG, null);

        // ////////////////////////////////////////////////////////////////////
        // Verify
        Mockito.verify(spyStore, Mockito.times(2)).create(ChangeSetDescriptor.class);
        List<ChangeSetDescriptor> changeSets = changeLogDescriptor.getChangeSets();
        Assert.assertEquals("ChangeSets expected.", 2, changeSets.size());

        for (ChangeSetDescriptor changeSetDescriptor : changeSets) {
            if (SET_ATTR_ID_COMPLETE.equals(changeSetDescriptor.getId())) {
                Assert.assertNotNull("ChangeSet attribute author is missing.", changeSetDescriptor.getAuthor());
                Assert.assertNotNull("ChangeSet atrribute 'comment' is missing.", changeSetDescriptor.getComment());
                Assert.assertNotNull("ChangeSet atrribute 'preCondition' is missing.", changeSetDescriptor.getPreconditions());
                Assert.assertNotNull("ChangeSet atrribute 'rollback' is missing.", changeSetDescriptor.getRollback());
            }
        }

        store.commitTransaction();
    }

    /**
     * Test whether an existing Include-node is reused when scanning a ChangeLog i.s.o. creating a new/further node. Attributes has to be applied.
     */
    @Test
    public void reuseIncludeNodeWhenScanningChangeLog() {

        File changeLogDir = new File(testClassesDir, DIR_CHANGELOG);
        File[] changeLogFiles = changeLogDir.listFiles();
        store.beginTransaction();

        // scan all files in folder
        for (File file : changeLogFiles) {
            scanner.scan(file, "/" + DIR_CHANGELOG + "/" + file.getName(), null);
        }

        // //////////////////////////////////////////////////////////////////
        // Verify
        List<ChangeLogDescriptor> changeLogs = query("MATCH (inc:ChangeLog) RETURN inc").getColumn("inc");
        for (ChangeLogDescriptor changeLog : changeLogs) {
            String fileName = changeLog.getFileName();
            if (StringUtils.endsWith(fileName, FILE_INCLUDED_BY_MASTER)) {
                Assert.assertNotNull("ChangeLog is included in master.xml, so Include#file has to be set.", changeLog.getFile());
                Assert.assertEquals("Include#file is an absolut path. So Include#file and File#fileName has to be equal.", changeLog.getFile(),
                        fileName);
            } else {
                Assert.assertNull("ChangeLog is nowhere included, so Include#file has to be null.", changeLog.getFile());
            }
        }

        store.commitTransaction();
    }

    /**
     * Tests whether known refactoring will be detected.
     */
    @Test
    public void detectKnownRefactorings() {
        File changeLogFile = new File(testClassesDir, DIR_CHANGELOG + "/" + FILE_ALL_KOWN_REFACTORINGS);
        store.beginTransaction();

        scanner.scan(changeLogFile, "/" + DIR_CHANGELOG + "/" + FILE_ALL_KOWN_REFACTORINGS, null);

        // ////////////////////////////////////////////////////////////////////
        // Verify
        Mockito.verify(spyStore, Mockito.times(1)).create(AddColumnDescriptor.class);
        Mockito.verify(spyStore, Mockito.times(1)).create(AddForeignKeyDescriptor.class);
        Mockito.verify(spyStore, Mockito.times(1)).create(AddNotNullConstraintDescriptor.class);
        Mockito.verify(spyStore, Mockito.times(1)).create(AddPrimaryKeyDescriptor.class);
        Mockito.verify(spyStore, Mockito.times(1)).create(AddUniqueConstraintDescriptor.class);
        Mockito.verify(spyStore, Mockito.times(1)).create(CreateSequenceDescriptor.class);
        Mockito.verify(spyStore, Mockito.times(1)).create(CreateTableDescriptor.class);
        Mockito.verify(spyStore, Mockito.times(1)).create(DropColumnDescriptor.class);
        Mockito.verify(spyStore, Mockito.times(1)).create(DropConstraintDescriptor.class);
        Mockito.verify(spyStore, Mockito.times(1)).create(DropTableDescriptor.class);
        Mockito.verify(spyStore, Mockito.times(1)).create(SqlDescriptor.class);

        store.commitTransaction();
    }

    @Override
    protected Scanner getScanner() {

        spyStore = Mockito.spy(store);
        try {
            return new ScannerImpl(spyStore, getScannerPluginRepository().getScannerPlugins());
        } catch (PluginRepositoryException e) {
            throw new IllegalStateException("Cannot get scanner plugins.", e);
        }
    }
}

// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.validation;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagConstraints;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.search.SearchCompiler.InDataSourceArea;
import org.openstreetmap.josm.data.osm.search.SearchCompiler.NotOutsideDataSourceArea;
import org.openstreetmap.josm.data.osm.visitor.OsmPrimitiveVisitor;
import org.openstreetmap.josm.data.preferences.sources.ValidatorPrefHelper;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Stopwatch;

/**
 * Parent class for all validation tests.
 * <p>
 * A test is a primitive visitor, so that it can access to all data to be
 * validated. These primitives are always visited in the same order: nodes
 * first, then ways.
 *
 * @author frsantos
 */
@SuppressWarnings("PMD.UnitTestShouldUseTestAnnotation")
public class Test implements OsmPrimitiveVisitor {

    protected static final Predicate<OsmPrimitive> IN_DOWNLOADED_AREA = new NotOutsideDataSourceArea();
    protected static final Predicate<OsmPrimitive> IN_DOWNLOADED_AREA_STRICT = new InDataSourceArea(true);

    /** Name of the test */
    protected final String name;

    /** Description of the test */
    protected final String description;

    /** Whether this test is enabled. Enabled by default */
    public boolean enabled = true;

    /** The preferences check for validation */
    protected JCheckBox checkEnabled;

    /** The preferences check for validation on upload */
    protected JCheckBox checkBeforeUpload;

    /** Whether this test must check before upload. Enabled by default */
    public boolean testBeforeUpload = true;

    /** Whether this test is performing just before an upload */
    protected boolean isBeforeUpload;

    /** The list of errors */
    protected List<TestError> errors = new ArrayList<>();

    /** Whether the test is run on a partial selection data */
    protected boolean partialSelection;

    /** the progress monitor to use */
    protected ProgressMonitor progressMonitor;

    /** the start time to compute elapsed time when test finishes */
    protected Stopwatch stopwatch;

    private boolean showElementCount;

    /**
     * Constructor
     * @param name Name of the test
     * @param description Description of the test
     */
    public Test(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * Constructor
     * @param name Name of the test
     */
    public Test(String name) {
        this(name, null);
    }

    /**
     * A test that forwards all primitives to {@link #check(OsmPrimitive)}.
     */
    public abstract static class TagTest extends Test {
        /**
         * Constructs a new {@code TagTest} with given name and description.
         * @param name The test name
         * @param description The test description
         */
        protected TagTest(String name, String description) {
            super(name, description);
        }

        /**
         * Constructs a new {@code TagTest} with given name.
         * @param name The test name
         */
        protected TagTest(String name) {
            super(name);
        }

        /**
         * Checks the tags of the given primitive.
         * @param p The primitive to test
         */
        public abstract void check(OsmPrimitive p);

        @Override
        public void visit(Node n) {
            check(n);
        }

        @Override
        public void visit(Way w) {
            check(w);
        }

        @Override
        public void visit(Relation r) {
            check(r);
        }

        protected boolean includeOtherSeverityChecks() {
            return isBeforeUpload ? ValidatorPrefHelper.PREF_OTHER_UPLOAD.get() : ValidatorPrefHelper.PREF_OTHER.get();
        }
    }

    /**
     * Initializes any global data used this tester.
     * @throws Exception When cannot initialize the test
     */
    public void initialize() throws Exception {
        this.stopwatch = Stopwatch.createStarted();
    }

    /**
     * Start the test using a given progress monitor
     *
     * @param progressMonitor  the progress monitor
     */
    public void startTest(ProgressMonitor progressMonitor) {
        this.progressMonitor = Optional.ofNullable(progressMonitor).orElse(NullProgressMonitor.INSTANCE);
        String startMessage = tr("Running test {0}", name);
        this.progressMonitor.beginTask(startMessage);
        Logging.debug(startMessage);
        this.errors = new ArrayList<>(30);
        this.stopwatch = Stopwatch.createStarted();
    }

    /**
     * Flag notifying that this test is run over a partial data selection
     * @param partialSelection Whether the test is on a partial selection data
     */
    public void setPartialSelection(boolean partialSelection) {
        this.partialSelection = partialSelection;
    }

    /**
     * Gets the validation errors accumulated until this moment.
     * @return The list of errors
     */
    public List<TestError> getErrors() {
        return errors;
    }

    /**
     * Notification of the end of the test. The tester may perform additional
     * actions and destroy the used structures.
     * <p>
     * If you override this method, don't forget to cleanup {@code progressMonitor}
     * (most overrides call {@code super.endTest()} to do this).
     */
    public void endTest() {
        progressMonitor.finishTask();
        progressMonitor = null;
        if (stopwatch.elapsed() > 0) {
            Logging.debug(stopwatch.toString(getName()));
        }
    }

    /**
     * Visits all primitives to be tested. These primitives are always visited
     * in the same order: nodes first, then ways.
     *
     * @param selection The primitives to be tested
     */
    public void visit(Collection<OsmPrimitive> selection) {
        if (progressMonitor != null) {
            progressMonitor.setTicksCount(selection.size());
        }
        long cnt = 0;
        for (OsmPrimitive p : selection) {
            if (isCanceled()) {
                break;
            }
            if (isPrimitiveUsable(p)) {
                p.accept(this);
            }
            if (progressMonitor != null) {
                progressMonitor.worked(1);
                cnt++;
                // add frequently changing info to progress monitor so that it
                // doesn't seem to hang when test takes long
                if (showElementCount && cnt % 1000 == 0) {
                    progressMonitor.setExtraText(tr("{0} of {1} elements done", cnt, selection.size()));
                }
            }
        }
    }

    /**
     * Determines if the primitive is usable for tests.
     * @param p The primitive
     * @return {@code true} if the primitive can be tested, {@code false} otherwise
     */
    public boolean isPrimitiveUsable(OsmPrimitive p) {
        return p.isUsable() && (!(p instanceof Way) || (((Way) p).getNodesCount() > 1)); // test only Ways with at least 2 nodes
    }

    @Override
    public void visit(Node n) {
        // To be overridden in subclasses
    }

    @Override
    public void visit(Way w) {
        // To be overridden in subclasses
    }

    @Override
    public void visit(Relation r) {
        // To be overridden in subclasses
    }

    /**
     * Allow the tester to manage its own preferences
     * @param testPanel The panel to add any preferences component
     */
    public void addGui(JPanel testPanel) {
        checkEnabled = new JCheckBox(name, enabled);
        checkEnabled.setToolTipText(description);
        testPanel.add(checkEnabled, GBC.std());

        GBC a = GBC.eol();
        a.anchor = GridBagConstraints.LINE_END;
        checkBeforeUpload = new JCheckBox();
        checkBeforeUpload.setSelected(testBeforeUpload);
        testPanel.add(checkBeforeUpload, a);
    }

    /**
     * Called when the used submits the preferences
     * @return {@code true} if restart is required, {@code false} otherwise
     */
    public boolean ok() {
        enabled = checkEnabled.isSelected();
        testBeforeUpload = checkBeforeUpload.isSelected();
        return false;
    }

    /**
     * Fixes the error with the appropriate command
     *
     * @param testError error to fix
     * @return The command to fix the error
     */
    public Command fixError(TestError testError) {
        return null;
    }

    /**
     * Returns true if the given error can be fixed automatically
     *
     * @param testError The error to check if can be fixed
     * @return true if the error can be fixed
     */
    public boolean isFixable(TestError testError) {
        return false;
    }

    /**
     * Returns true if this plugin must check the uploaded data before uploading
     * @return true if this plugin must check the uploaded data before uploading
     */
    public boolean testBeforeUpload() {
        return testBeforeUpload;
    }

    /**
     * Sets the flag that marks an upload check
     * @param isUpload if true, the test is before upload
     */
    public void setBeforeUpload(boolean isUpload) {
        this.isBeforeUpload = isUpload;
    }

    /**
     * Returns the test name.
     * @return The test name
     */
    public String getName() {
        return name;
    }

    /**
     * Determines if the test has been canceled.
     * @return {@code true} if the test has been canceled, {@code false} otherwise
     */
    public boolean isCanceled() {
        return progressMonitor != null && progressMonitor.isCanceled();
    }

    /**
     * Build a Delete command on all primitives that have not yet been deleted manually by user, or by another error fix.
     * If all primitives have already been deleted, null is returned.
     * @param primitives The primitives wanted for deletion
     * @return a Delete command on all primitives that have not yet been deleted, or null otherwise
     */
    protected final Command deletePrimitivesIfNeeded(Collection<? extends OsmPrimitive> primitives) {
        Collection<OsmPrimitive> primitivesToDelete = primitives.stream()
                .filter(p -> !p.isDeleted())
                .collect(Collectors.toList());
        if (!primitivesToDelete.isEmpty()) {
            return DeleteCommand.delete(primitivesToDelete);
        } else {
            return null;
        }
    }

    /**
     * Determines if the specified primitive denotes a building.
     * @param p The primitive to be tested
     * @return True if building key is set and different from no,entrance
     */
    protected static final boolean isBuilding(OsmPrimitive p) {
        return p.hasTagDifferent("building", "no", "entrance");
    }

    /**
     * Determines if the specified primitive denotes a residential area.
     * @param p The primitive to be tested
     * @return True if landuse key is equal to residential
     */
    protected static final boolean isResidentialArea(OsmPrimitive p) {
        return p.hasTag("landuse", "residential");
    }

    /**
     * Free resources.
     */
    public void clear() {
        errors.clear();
    }

    protected void setShowElements(boolean b) {
        showElementCount = b;
    }

    /**
     * Returns the name of this class.
     * @return the name of this class (for ToolTip)
     * @since 15972
     */
    public Object getSource() {
        return "Java: " + this.getClass().getName();
    }

    /**
     * Filter the list of errors, remove all which do not concern the given list of primitives
     * @param given the list of primitives
     * @since 18960
     */
    public void removeIrrelevantErrors(Collection<? extends OsmPrimitive> given) {
        if (errors == null || errors.isEmpty())
            return;
        // filter errors for those which are needed, don't show errors for objects which were not in the selection
        final Set<? extends OsmPrimitive> relevant;
        if (given instanceof Set) {
            relevant = (Set<? extends OsmPrimitive>) given;
        } else {
            relevant = new HashSet<>(given);
        }
        errors.removeIf(e -> !e.isConcerned(relevant));
    }
}

// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.gpx.GpxConstants;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.data.gpx.IGpxTrack;
import org.openstreetmap.josm.data.gpx.IGpxTrackSegment;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.datatransfer.ClipboardUtils;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.testutils.mockers.ExtendedDialogMocker;
import org.openstreetmap.josm.tools.Logging;

/**
 * Unit tests of {@link OsmDataLayer} class.
 */
@Main
@Projection
class OsmDataLayerTest {
    private DataSet ds;
    private OsmDataLayer layer;

    /**
     * Setup tests
     */
    @BeforeEach
    public void setUp() {
        ds = new DataSet();
        layer = new OsmDataLayer(ds, "", null);
        MainApplication.getLayerManager().addLayer(layer);
    }

    /**
     * Unit test of {@link OsmDataLayer#setRecentRelation} and {@link OsmDataLayer#getRecentRelations}.
     */
    @Test
    void testRecentRelation() {
        int n = OsmDataLayer.PROPERTY_RECENT_RELATIONS_NUMBER.get();
        assertTrue(n > 0);
        for (int i = 0; i < 2*n; i++) {
            Relation r = new Relation(i, 1);
            ds.addPrimitive(r);
            layer.setRecentRelation(r);
        }
        assertEquals(n, layer.getRecentRelations().size());
        for (OsmPrimitive r : ds.allPrimitives()) {
            if (r instanceof Relation) {
                layer.removeRecentRelation((Relation) r);
            }
        }
        assertTrue(layer.getRecentRelations().isEmpty());
    }

    /**
     * Unit test of {@link OsmDataLayer#getInfoComponent}.
     */
    @Test
    void testGetInfoComponent() {
        assertNotNull(layer.getInfoComponent());

        layer.setUploadDiscouraged(true);

        fillDataSet(ds);

        assertNotNull(layer.getInfoComponent());
    }

    private void fillDataSet(DataSet ds) {
        Node n = new Node(1, 2);
        n.setCoor(LatLon.ZERO);
        n.setDeleted(true);
        n.setVisible(false);
        ds.addPrimitive(n);
        n = new Node(2, 2);
        n.setCoor(LatLon.ZERO);
        ds.addPrimitive(n);

        Way w = new Way(1, 2);
        w.setDeleted(true);
        w.setVisible(false);
        ds.addPrimitive(w);
        ds.addPrimitive(new Way(2, 2));

        Relation r = new Relation(1, 2);
        r.setDeleted(true);
        r.setVisible(false);
        ds.addPrimitive(r);
        ds.addPrimitive(new Relation(2, 2));
    }

    /**
     * Unit test of {@link OsmDataLayer#addLayerStateChangeListener}.
     */
    @Test
    void testLayerStateChangeListenerNull() {
        layer.addLayerStateChangeListener(null);
    }

    /**
     * Unit test of {@link OsmDataLayer#getIcon}.
     */
    @Test
    void testGetIcon() {
        assertNotNull(layer.getIcon());
        layer.setUploadDiscouraged(true);
        assertNotNull(layer.getIcon());
    }

    /**
     * Unit test of {@link OsmDataLayer#paint}.
     */
    @Test
    void testPaint() {
        fillDataSet(ds);
        assertNotNull(MainApplication.getMap());
        layer.paint(TestUtils.newGraphics(), MainApplication.getMap().mapView, new Bounds(LatLon.ZERO));
    }

    /**
     * Unit test of {@link OsmDataLayer#getToolTipText}.
     */
    @Test
    void testGetToolTipText() {
        assertEquals("<html>0 nodes<br>0 ways<br>0 relations</html>", new OsmDataLayer(ds, "", null).getToolTipText());
        fillDataSet(ds);
        assertEquals("<html>1 node<br>1 way<br>1 relation</html>", new OsmDataLayer(ds, "", null).getToolTipText());
        assertEquals("<html>1 node<br>1 way<br>1 relation<br>data.osm</html>", new OsmDataLayer(ds, "", new File("data.osm")).getToolTipText());
    }

    /**
     * Unit test of {@link OsmDataLayer#mergeFrom}.
     */
    @Test
    void testMergeFrom() {
        fillDataSet(ds);
        OsmDataLayer layer2 = new OsmDataLayer(new DataSet(), "", null);
        MainApplication.getLayerManager().addLayer(layer2);
        assertTrue(layer2.data.allPrimitives().isEmpty());
        assertTrue(layer2.isMergable(layer));
        layer2.mergeFrom(layer);
        assertEquals(6, layer2.data.allPrimitives().size());
        layer.setUploadDiscouraged(true);
        layer2.mergeFrom(layer);
        assertTrue(layer2.isUploadDiscouraged());
    }

    /**
     * Unit test of {@link OsmDataLayer#cleanupAfterUpload}.
     */
    @Test
    void testCleanupAfterUpload() {
        fillDataSet(ds);
        assertEquals(6, layer.data.allPrimitives().size());
        layer.cleanupAfterUpload(ds.allPrimitives());
        assertEquals(3, layer.data.allPrimitives().size());
    }

    /**
     * Unit test of {@link OsmDataLayer#getMenuEntries}.
     */
    @Test
    void testGetMenuEntries() {
        ExpertToggleAction.getInstance().setExpert(true);
        assertEquals(17, layer.getMenuEntries().length);

        ExpertToggleAction.getInstance().setExpert(false);
        assertEquals(14, layer.getMenuEntries().length);
    }

    /**
     * Unit test of {@link OsmDataLayer#toGpxData}.
     * @throws IllegalDataException never
     */
    @Test
    void testToGpxData() throws IllegalDataException {
        ds.mergeFrom(OsmReader.parseDataSet(new ByteArrayInputStream((
                "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<osm version='0.6' upload='false' generator='JOSM'>\n" +
                "  <node id='-546306' timestamp='2018-08-01T10:00:00Z' lat='47.0' lon='9.0'>\n" +
                "    <tag k='gpx:ele' v='123' />\n" +
                "    <tag k='gpx:time' v='2018-08-01T10:00:00Z' />\n" +
                "  </node>\n" +
                "  <node id='-546307' timestamp='2018-08-01T10:01:00Z' lat='47.1' lon='9.1'>\n" +
                "    <tag k='ele' v='456' />\n" +
                "    <tag k='gpx:time' v='2018-08-01T10:01:00Z' />\n" +
                "  </node>\n" +
                "  <node id='-546308' timestamp='2018-08-01T10:02:00Z' lat='47.05' lon='9.05'>\n" +
                "    <tag k='ele' v='789' />\n" +
                "  </node>\n" +
                "  <way id='-546309'>\n" +
                "    <nd ref='-546306' />\n" +
                "    <nd ref='-546307' />\n" +
                "    <nd ref='-546308' />\n" +
                "  </way>\r\n" +
                "</osm>").getBytes(StandardCharsets.UTF_8)), null));
        GpxData gpx = layer.toGpxData();
        assertNotNull(gpx);
        // Check metadata
        assertEquals(new Bounds(47.0, 9.0, 47.1, 9.1), gpx.recalculateBounds());
        // Check there is no waypoint
        assertTrue(gpx.getWaypoints().isEmpty());
        // Check that track is correct
        assertEquals(1, gpx.getTrackCount());
        IGpxTrack track = gpx.getTracks().iterator().next();
        Collection<IGpxTrackSegment> segments = track.getSegments();
        assertEquals(1, segments.size());
        Collection<WayPoint> trackpoints = segments.iterator().next().getWayPoints();
        assertEquals(3, trackpoints.size());
        Iterator<WayPoint> it = trackpoints.iterator();
        WayPoint p1 = it.next();
        assertEquals(new LatLon(47.0, 9.0), p1.getCoor());
        assertEquals(123, (double) p1.get(GpxConstants.PT_ELE));
        assertEquals("2018-08-01T10:00:00Z", String.valueOf(p1.get(GpxConstants.PT_TIME)));
        WayPoint p2 = it.next();
        assertEquals(new LatLon(47.1, 9.1), p2.getCoor());
        assertEquals(456, (double) p2.get(GpxConstants.PT_ELE));
        assertEquals("2018-08-01T10:01:00Z", String.valueOf(p2.get(GpxConstants.PT_TIME)));
        WayPoint p3 = it.next();
        assertEquals(new LatLon(47.05, 9.05), p3.getCoor());
        assertEquals(789, (double) p3.get(GpxConstants.PT_ELE));
        assertEquals("2018-08-01T10:02:00Z", String.valueOf(p3.get(GpxConstants.PT_TIME)));
    }

    /**
     * Unit test of {@link OsmDataLayer#containsPoint}.
     */
    @Test
    void testContainsPoint() {
        fillDataSet(ds);
        assertTrue(layer.containsPoint(LatLon.ZERO));
    }

    /**
     * Unit test of {@link OsmDataLayer#isModified}.
     */
    @Test
    void testIsModified() {
        assertFalse(layer.isModified());
        fillDataSet(ds);
        assertTrue(layer.isModified());
    }

    /**
     * Unit test of {@link OsmDataLayer#projectionChanged}.
     */
    @Test
    void testProjectionChanged() {
        layer.projectionChanged(null, null);
    }

    /**
     * Unit test of {@link OsmDataLayer#checkSaveConditions}.
     */
    @Test
    void testCheckSaveConditions() {
        TestUtils.assumeWorkingJMockit();
        final ExtendedDialogMocker edMocker = new ExtendedDialogMocker(
            Collections.singletonMap("The layer contains no data.", "Cancel")
        );

        assertFalse(layer.checkSaveConditions());
        fillDataSet(ds);
        assertTrue(layer.checkSaveConditions());

        assertEquals(1, edMocker.getInvocationLog().size());
        Object[] invocationLogEntry = edMocker.getInvocationLog().get(0);
        assertEquals(2, (int) invocationLogEntry[0]);
        assertEquals("Empty layer", invocationLogEntry[2]);
    }

    /**
     * Checks that unnamed layer number increases
     */
    @Test
    void testLayerNameIncreases() {
        final OsmDataLayer layer1 = new OsmDataLayer(new DataSet(), OsmDataLayer.createLayerName(147), null);
        final OsmDataLayer layer2 = new OsmDataLayer(new DataSet(), OsmDataLayer.createNewName(), null);
        assertEquals("Data Layer 147", layer1.getName());
        assertEquals("Data Layer 148", layer2.getName());
    }

    /**
     * Checks that named layer got no number
     */
    @Test
    void testLayerUnnumberedName() {
        final OsmDataLayer layer = new OsmDataLayer(new DataSet(), "Data Layer ", null);
        assertEquals("Data Layer ", layer.getName());
    }

    /**
     * Non-regression test for ticket #13985
     */
    @Test
    void testLayerNameDoesFinish() {
        final OsmDataLayer layer = new OsmDataLayer(new DataSet(), "Data Layer from GeoJSON: foo.geojson", null);
        assertEquals("Data Layer from GeoJSON: foo.geojson", layer.getName());
    }

    /**
     * Non-regression test for ticket <a href="https://josm.openstreetmap.de/ticket/17065">#17065</a>.
     */
    @Test
    void testTicket17065() {
        ClipboardUtils.clear();
        Logging.clearLastErrorAndWarnings();
        new OsmDataLayer(new DataSet(), null, null).destroy();
        assertTrue(Logging.getLastErrorAndWarnings().stream().noneMatch(s -> s.contains("UnsupportedFlavorException")));
    }

    /**
     * Non-regression test for #23950
     */
    @Test
    void testTicket23950() {
        final Relation first = TestUtils.newRelation("", new RelationMember("", TestUtils.newNode("")));
        final DataSet ds = new DataSet();
        // This is needed to cause the condition
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(ds, "StyledTiledMapRendererTest#testRecursiveRelation", null));
        ds.addPrimitiveRecursive(first);
        first.addMember(new RelationMember("", first));
        ds.setSelected(first);
    }

}

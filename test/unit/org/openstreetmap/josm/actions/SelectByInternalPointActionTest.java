// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.Projection;

import net.trajano.commons.testing.UtilityClassTestUtil;

/**
 * Unit tests for class {@link SelectByInternalPointAction}.
 */
@BasicPreferences
@Main
@Projection
final class SelectByInternalPointActionTest {
    /**
     * Tests that {@code SelectByInternalPointAction} satisfies utility class criteria.
     * @throws ReflectiveOperationException if an error occurs
     */
    @Test
    void testUtilityClass() throws ReflectiveOperationException {
        UtilityClassTestUtil.assertUtilityClassWellDefined(SelectByInternalPointAction.class);
    }

    /**
     * Unit test - no dataset.
     */
    @Test
    void testNoDataSet() {
        assertNull(MainApplication.getLayerManager().getEditDataSet());
        assertEquals(0, SelectByInternalPointAction.getSurroundingObjects(null).size());
        assertNull(SelectByInternalPointAction.getSmallestSurroundingObject(null));
        SelectByInternalPointAction.performSelection(null, false, false);
    }

    static Layer initDataSet() {
        DataSet ds = new DataSet();
        Node n1 = new Node(new EastNorth(1, 1));
        Node n2 = new Node(new EastNorth(1, 2));
        Node n3 = new Node(new EastNorth(2, 2));
        Node n4 = new Node(new EastNorth(2, 1));
        ds.addPrimitive(n1);
        ds.addPrimitive(n2);
        ds.addPrimitive(n3);
        ds.addPrimitive(n4);
        Way w = new Way();
        w.addNode(n1);
        w.addNode(n2);
        w.addNode(n3);
        w.addNode(n4);
        w.addNode(n1);
        assertTrue(w.isClosed());
        ds.addPrimitive(w);
        Relation r = new Relation();
        r.addMember(new RelationMember("outer", w));
        ds.addPrimitive(r);
        OsmDataLayer layer = new OsmDataLayer(ds, "", null);
        MainApplication.getLayerManager().addLayer(layer);
        return layer;
    }

    /**
     * Unit test of {@link SelectByInternalPointAction#getSurroundingObjects} method.
     */
    @Test
    void testGetSurroundingObjects() {
        initDataSet();
        assertEquals(0, SelectByInternalPointAction.getSurroundingObjects(null).size());
        assertEquals(0, SelectByInternalPointAction.getSurroundingObjects(new EastNorth(0, 0)).size());
        assertEquals(1, SelectByInternalPointAction.getSurroundingObjects(new EastNorth(1.5, 1.5)).size());
        assertEquals(0, SelectByInternalPointAction.getSurroundingObjects(new EastNorth(3, 3)).size());
    }

    /**
     * Unit test of {@link SelectByInternalPointAction#getSmallestSurroundingObject} method.
     */
    @Test
    void testGetSmallestSurroundingObject() {
        initDataSet();
        assertNull(SelectByInternalPointAction.getSmallestSurroundingObject(null));
        assertNotNull(SelectByInternalPointAction.getSmallestSurroundingObject(new EastNorth(1.5, 1.5)));
    }

    /**
     * Unit test of {@link SelectByInternalPointAction#performSelection} method.
     */
    @Test
    void testPerformSelection() {
        initDataSet();
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();

        assertEquals(0, ds.getSelected().size());
        SelectByInternalPointAction.performSelection(null, false, false);
        assertEquals(0, ds.getSelected().size());
        SelectByInternalPointAction.performSelection(new EastNorth(0, 0), false, false);
        assertEquals(0, ds.getSelected().size());
        SelectByInternalPointAction.performSelection(new EastNorth(1.5, 1.5), false, false);
        assertEquals(1, ds.getSelected().size());
        ds.clearSelection();
        ds.addSelected(ds.getNodes());
        assertEquals(4, ds.getSelected().size());
        SelectByInternalPointAction.performSelection(new EastNorth(1.5, 1.5), true, false);
        assertEquals(5, ds.getSelected().size());
        SelectByInternalPointAction.performSelection(new EastNorth(1.5, 1.5), false, true);
        assertEquals(4, ds.getSelected().size());
    }
}

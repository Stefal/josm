// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.InvalidPathException;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.DefaultLayer;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryType;
import org.openstreetmap.josm.data.imagery.LayerDetails;
import org.openstreetmap.josm.data.imagery.TemplatedWMSTileSource;
import org.openstreetmap.josm.data.imagery.WMTSTileSource;
import org.openstreetmap.josm.data.imagery.WMTSTileSource.Layer;
import org.openstreetmap.josm.data.imagery.WMTSTileSource.WMTSGetCapabilitiesException;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.AlignImageryPanel;
import org.openstreetmap.josm.gui.layer.ImageryLayer;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.gui.preferences.imagery.WMSLayerTree;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.imagery.WMSImagery;
import org.openstreetmap.josm.io.imagery.WMSImagery.WMSGetCapabilitiesException;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;
import org.openstreetmap.josm.tools.bugreport.ReportedException;

/**
 * Action displayed in imagery menu to add a new imagery layer.
 * @since 3715
 */
public class AddImageryLayerAction extends JosmAction implements AdaptableAction {
    private final transient ImageryInfo info;

    static class SelectWmsLayersDialog extends ExtendedDialog {
        SelectWmsLayersDialog(WMSLayerTree tree, JComboBox<String> formats) {
            super(MainApplication.getMainFrame(), tr("Select WMS layers"), tr("Add layers"), tr("Cancel"));
            final JScrollPane scrollPane = new JScrollPane(tree.getLayerTree());
            scrollPane.setPreferredSize(new Dimension(400, 400));
            final JPanel panel = new JPanel(new GridBagLayout());
            panel.add(scrollPane, GBC.eol().fill());
            panel.add(formats, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
            setContent(panel);
        }
    }

    /**
     * Constructs a new {@code AddImageryLayerAction} for the given {@code ImageryInfo}.
     * If an http:// icon is specified, it is fetched asynchronously.
     * @param info The imagery info
     */
    public AddImageryLayerAction(ImageryInfo info) {
        super(info.getMenuName(), /* ICON */"imagery_menu", info.getToolTipText(), null,
                true, ToolbarPreferences.IMAGERY_PREFIX + info.getToolbarName(), false);
        setHelpId(ht("/Preferences/Imagery"));
        this.info = info;
        installAdapters();

        // change toolbar icon from if specified
        String icon = info.getIcon();
        if (icon != null) {
            new ImageProvider(icon).setOptional(true).getResourceAsync(result -> {
                if (result != null) {
                    GuiHelper.runInEDT(() -> result.attachImageIcon(this));
                }
            });
        }
    }

    /**
     * Converts general ImageryInfo to specific one, that does not need any user action to initialize
     * see: https://josm.openstreetmap.de/ticket/13868
     * @param info ImageryInfo that will be converted (or returned when no conversion needed)
     * @return ImageryInfo object that's ready to be used to create TileSource
     */
    private static ImageryInfo convertImagery(ImageryInfo info) {
        try {
            if (info.getUrl() != null && info.getUrl().contains("{time}")) {
                final String instant = Year.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString();
                final String example = String.join("/", instant, instant);
                final String initialSelectionValue = info.getDate() != null ? info.getDate() : example;
                final String userDate = JOptionPane.showInputDialog(MainApplication.getMainFrame(),
                        tr("Time filter for \"{0}\" such as \"{1}\"", info.getName(), example),
                        initialSelectionValue);
                if (userDate == null) {
                    return null;
                }
                info.setDate(userDate);
                // TODO persist new {time} value (via ImageryLayerInfo.save?)
            }
            switch (info.getImageryType()) {
            case WMS_ENDPOINT:
                // convert to WMS type
                if (Utils.isEmpty(info.getDefaultLayers())) {
                    return getWMSLayerInfo(info);
                } else {
                    return info;
                }
            case WMTS:
                // specify which layer to use
                if (Utils.isEmpty(info.getDefaultLayers())) {
                    WMTSTileSource tileSource = new WMTSTileSource(info);
                    DefaultLayer layerId = tileSource.userSelectLayer();
                    if (layerId != null) {
                        ImageryInfo copy = new ImageryInfo(info);
                        copy.setDefaultLayers(Collections.singletonList(layerId));
                        String layerName = tileSource.getLayers().stream()
                                .filter(x -> x.getIdentifier().equals(layerId.getLayerName()))
                                .map(Layer::getUserTitle)
                                .findFirst()
                                .orElse("");
                        copy.setName(copy.getName() + ": " + layerName);
                        return copy;
                    }
                    return null;
                } else {
                    return info;
                }
            default:
                return info;
            }
        } catch (MalformedURLException ex) {
            handleException(ex, tr("Invalid service URL."), tr("WMS Error"), null);
        } catch (IOException ex) {
            handleException(ex, tr("Could not retrieve WMS layer list."), tr("WMS Error"), null);
        } catch (WMSGetCapabilitiesException ex) {
            handleException(ex, tr("Could not parse WMS layer list."), tr("WMS Error"),
                    "Could not parse WMS layer list. Incoming data:\n" + ex.getIncomingData());
        } catch (WMTSGetCapabilitiesException ex) {
            handleException(ex, tr("Could not parse WMTS layer list."), tr("WMTS Error"),
                    "Could not parse WMTS layer list.");
        }
        return null;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!isEnabled()) return;
        ImageryLayer layer = null;
        try {
            final ImageryInfo infoToAdd = convertImagery(info);
            if (infoToAdd != null) {
                layer = ImageryLayer.create(infoToAdd);
                getLayerManager().addLayer(layer, false);
                AlignImageryPanel.addNagPanelIfNeeded(infoToAdd);
            }
        } catch (IllegalArgumentException | ReportedException ex) {
            if (Utils.isEmpty(ex.getMessage()) || GraphicsEnvironment.isHeadless()) {
                throw ex;
            } else {
                Logging.error(ex);
                JOptionPane.showMessageDialog(MainApplication.getMainFrame(), ex.getMessage(), tr("Error"), JOptionPane.ERROR_MESSAGE);
                if (layer != null) {
                    getLayerManager().removeLayer(layer);
                }
            }
        }
    }

    /**
     * Represents the user choices when selecting layers to display.
     * @since 14549
     */
    public static class LayerSelection {
        private final List<LayerDetails> layers;
        private final String format;
        private final boolean transparent;

        /**
         * Constructs a new {@code LayerSelection}.
         * @param layers selected layers
         * @param format selected image format
         * @param transparent enable transparency?
         */
        public LayerSelection(List<LayerDetails> layers, String format, boolean transparent) {
            this.layers = layers;
            this.format = format;
            this.transparent = transparent;
        }
    }

    private static LayerSelection askToSelectLayers(WMSImagery wms) {
        final WMSLayerTree tree = new WMSLayerTree();

        Collection<String> wmsFormats = wms.getFormats();
        final JComboBox<String> formats = new JComboBox<>(wmsFormats.toArray(new String[0]));
        formats.setSelectedItem(wms.getPreferredFormat());
        formats.setToolTipText(tr("Select image format for WMS layer"));

        JCheckBox checkBounds = new JCheckBox(tr("Show only layers for current view"), true);
        Runnable updateTree = () -> {
            LatLon latLon = checkBounds.isSelected() && MainApplication.isDisplayingMapView()
                    ? MainApplication.getMap().mapView.getProjection().eastNorth2latlon(MainApplication.getMap().mapView.getCenter())
                    : null;
            tree.setCheckBounds(latLon);
            tree.updateTree(wms);
            System.out.println(wms);
        };
        checkBounds.addActionListener(ignore -> updateTree.run());
        updateTree.run();

        if (!GraphicsEnvironment.isHeadless()) {
            ExtendedDialog dialog = new ExtendedDialog(MainApplication.getMainFrame(),
                    tr("Select WMS layers"), tr("Add layers"), tr("Cancel"));
            final JScrollPane scrollPane = new JScrollPane(tree.getLayerTree());
            scrollPane.setPreferredSize(new Dimension(400, 400));
            final JPanel panel = new JPanel(new GridBagLayout());
            panel.add(scrollPane, GBC.eol().fill());
            panel.add(checkBounds, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
            panel.add(formats, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
            dialog.setContent(panel);

            if (dialog.showDialog().getValue() != 1) {
                return null;
            }
        }
        return new LayerSelection(
                tree.getSelectedLayers(),
                (String) formats.getSelectedItem(),
                true); // TODO: ask the user if transparent layer is wanted
    }

    /**
     * Asks user to choose a WMS layer from a WMS endpoint.
     * @param info the WMS endpoint.
     * @return chosen WMS layer, or null
     * @throws IOException if any I/O error occurs while contacting the WMS endpoint
     * @throws WMSGetCapabilitiesException if the WMS getCapabilities request fails
     * @throws InvalidPathException if a Path object cannot be constructed for the capabilities cached file
     */
    protected static ImageryInfo getWMSLayerInfo(ImageryInfo info) throws IOException, WMSGetCapabilitiesException {
        try {
            return getWMSLayerInfo(info, AddImageryLayerAction::askToSelectLayers);
        } catch (MalformedURLException ex) {
            handleException(ex, tr("Invalid service URL."), tr("WMS Error"), null);
        } catch (IOException ex) {
            handleException(ex, tr("Could not retrieve WMS layer list."), tr("WMS Error"), null);
        } catch (WMSGetCapabilitiesException ex) {
            handleException(ex, tr("Could not parse WMS layer list."), tr("WMS Error"),
                    "Could not parse WMS layer list. Incoming data:\n" + ex.getIncomingData());
        }
        return null;
    }

    /**
     * Asks user to choose a WMS layer from a WMS endpoint.
     * @param info the WMS endpoint.
     * @param choice how the user may choose the WMS layer
     * @return chosen WMS layer, or null
     * @throws IOException if any I/O error occurs while contacting the WMS endpoint
     * @throws WMSGetCapabilitiesException if the WMS getCapabilities request fails
     * @throws InvalidPathException if a Path object cannot be constructed for the capabilities cached file
     * @since 14549
     */
    public static ImageryInfo getWMSLayerInfo(ImageryInfo info, Function<WMSImagery, LayerSelection> choice)
            throws IOException, WMSGetCapabilitiesException {
        CheckParameterUtil.ensureThat(ImageryType.WMS_ENDPOINT == info.getImageryType(), "wms_endpoint imagery type expected");
        // We need to get the URL with {apikey} replaced. See #22642.
        final TemplatedWMSTileSource tileSource = new TemplatedWMSTileSource(info, ProjectionRegistry.getProjection());
        final WMSImagery wms = new WMSImagery(tileSource.getBaseUrl(), info.getCustomHttpHeaders());
        LayerSelection selection = choice.apply(wms);
        if (selection == null) {
            return null;
        }

        final String url = wms.buildGetMapUrl(
                selection.layers.stream().map(LayerDetails::getName).collect(Collectors.toList()),
                (List<String>) null,
                selection.format,
                selection.transparent
                );

        String selectedLayers = selection.layers.stream()
                .map(LayerDetails::getName)
                .collect(Collectors.joining(", "));
        // Use full copy of original Imagery info to copy all attributes. Only overwrite what's different
        ImageryInfo ret = new ImageryInfo(info);
        ret.setUrl(url);
        ret.setImageryType(ImageryType.WMS);
        ret.setName(info.getName() + " - " + selectedLayers);
        ret.setServerProjections(wms.getServerProjections(selection.layers));
        return ret;
    }

    private static void handleException(Exception ex, String uiMessage, String uiTitle, String logMessage) {
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(MainApplication.getMainFrame(), uiMessage, uiTitle, JOptionPane.ERROR_MESSAGE);
        }
        Logging.log(Logging.LEVEL_ERROR, logMessage, ex);
    }

    @Override
    protected boolean listenToSelectionChange() {
        return false;
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(!info.isBlacklisted());
    }

    @Override
    public String toString() {
        return "AddImageryLayerAction [info=" + info + ']';
    }
}

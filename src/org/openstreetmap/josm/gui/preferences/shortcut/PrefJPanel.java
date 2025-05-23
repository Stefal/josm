// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.shortcut;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.im.InputContext;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

import org.openstreetmap.josm.data.preferences.NamedColorProperty;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.util.TableHelper;
import org.openstreetmap.josm.gui.widgets.FilterField;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.KeyboardUtils;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * This is the keyboard preferences content.
 */
public class PrefJPanel extends JPanel {

    // table of shortcuts
    private final AbstractTableModel model;
    // this are the display(!) texts for the checkboxes. Let the JVM do the i18n for us <g>.
    // Ok, there's a real reason for this: The JVM should know best how the keys are labelled
    // on the physical keyboard. What language pack is installed in JOSM is completely
    // independent from the keyboard's labelling. But the operation system's locale
    // usually matches the keyboard. This even works with my English Windows and my German keyboard.
    private static final String SHIFT = InputEvent.getModifiersExText(KeyStroke.getKeyStroke(KeyEvent.VK_A,
            InputEvent.SHIFT_DOWN_MASK).getModifiers());
    private static final String CTRL = InputEvent.getModifiersExText(KeyStroke.getKeyStroke(KeyEvent.VK_A,
            InputEvent.CTRL_DOWN_MASK).getModifiers());
    private static final String ALT = InputEvent.getModifiersExText(KeyStroke.getKeyStroke(KeyEvent.VK_A,
            InputEvent.ALT_DOWN_MASK).getModifiers());
    private static final String META = InputEvent.getModifiersExText(KeyStroke.getKeyStroke(KeyEvent.VK_A,
            InputEvent.META_DOWN_MASK).getModifiers());

    // A list of keys to present the user. Sadly this really is a list of keys Java knows about,
    // not a list of real physical keys. If someone knows how to get that list?
    private static final Map<Integer, String> keyList = setKeyList();

    private final JCheckBox cbAlt = new JCheckBox();
    private final JCheckBox cbCtrl = new JCheckBox();
    private final JCheckBox cbMeta = new JCheckBox();
    private final JCheckBox cbShift = new JCheckBox();
    private final JCheckBox cbDefault = new JCheckBox();
    private final JCheckBox cbDisable = new JCheckBox();
    private final JosmComboBox<String> tfKey = new JosmComboBox<>();

    private final JTable shortcutTable = new JTable();
    private final FilterField filterField;

    /** Creates new form prefJPanel */
    public PrefJPanel() {
        this.model = new ScListModel();
        this.filterField = new FilterField();
        initComponents();
    }

    private static Map<Integer, String> setKeyList() {
        Map<Integer, String> list = new LinkedHashMap<>();
        String unknown = Toolkit.getProperty("AWT.unknown", "Unknown");
        // Assume all known keys are declared in KeyEvent as "public static int VK_*"
        for (Field field : KeyEvent.class.getFields()) {
            // Ignore VK_KP_DOWN, UP, etc. because they have the same name as VK_DOWN, UP, etc. See #8340
            if (field.getName().startsWith("VK_") && !field.getName().startsWith("VK_KP_")) {
                try {
                    int i = field.getInt(null);
                    String s = KeyEvent.getKeyText(i);
                    if (!s.isEmpty() && !s.contains(unknown)) {
                        list.put(i, s);
                    }
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    Logging.error(e);
                }
            }
        }
        KeyboardUtils.getExtendedKeyCodes(InputContext.getInstance().getLocale())
                .forEach((key, value) -> list.put(key, value.toString()));
        list.put(-1, "");

        // Remove "look-alike" values. See JOSM #22020 comment 2. These override the standard left/right/up/down keys.
        list.remove(KeyboardUtils.EXTENDED_KEYCODE_FLAG + 0x2190); // '←' (LEFTWARDS ARROW)
        list.remove(KeyboardUtils.EXTENDED_KEYCODE_FLAG + 0x2191); // '↑' (UPWARDS ARROW)
        list.remove(KeyboardUtils.EXTENDED_KEYCODE_FLAG + 0x2192); // '→' (RIGHTWARDS ARROW)
        list.remove(KeyboardUtils.EXTENDED_KEYCODE_FLAG + 0x2193); // '↓' (DOWNWARDS ARROW)
        return list;
    }

    /**
     * Show only shortcuts with descriptions containing given substring
     * @param substring The substring used to filter
     */
    public void filter(String substring) {
        filterField.setText(substring);
    }

    private static class ScListModel extends AbstractTableModel {
        private final String[] columnNames = {tr("Action"), tr("Shortcut")};
        private final transient List<Shortcut> data;

        /**
         * Constructs a new {@code ScListModel}.
         */
        ScListModel() {
            data = Shortcut.listAll();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public String getColumnName(int col) {
            return columnNames[col];
        }

        @Override
        public Object getValueAt(int row, int col) {
            return (col == 0) ? data.get(row).getLongText() : data.get(row);
        }
    }

    private class ShortcutTableCellRenderer extends DefaultTableCellRenderer {

        private final transient NamedColorProperty SHORTCUT_BACKGROUND_USER_COLOR = new NamedColorProperty(
                marktr("Shortcut Background: User"),
                new Color(200, 255, 200));
        private final transient NamedColorProperty SHORTCUT_BACKGROUND_MODIFIED_COLOR = new NamedColorProperty(
                marktr("Shortcut Background: Modified"),
                new Color(255, 255, 200));

        private final boolean name;

        ShortcutTableCellRenderer(boolean name) {
            this.name = name;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean
                isSelected, boolean hasFocus, int row, int column) {
            int row1 = shortcutTable.convertRowIndexToModel(row);
            Shortcut sc = (Shortcut) model.getValueAt(row1, -1);
            if (sc == null)
                return null;
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                table, name ? sc.getLongText() : sc.getKeyText(), isSelected, hasFocus, row, column);
            GuiHelper.setBackgroundReadable(label, UIManager.getColor("Table.background"));
            if (sc.isAssignedUser()) {
                GuiHelper.setBackgroundReadable(label, SHORTCUT_BACKGROUND_USER_COLOR.get());
            } else if (!sc.isAssignedDefault()) {
                GuiHelper.setBackgroundReadable(label, SHORTCUT_BACKGROUND_MODIFIED_COLOR.get());
            }
            return label;
        }
    }

    private void initComponents() {
        CbAction action = new CbAction(this);
        GBC gbc = GBC.eol().insets(3).fill(GBC.HORIZONTAL);

        setLayout(new GridBagLayout());
        add(buildFilterPanel(), gbc);

        // This is the list of shortcuts:
        TableHelper.setFont(shortcutTable, getClass());
        shortcutTable.setModel(model);
        shortcutTable.getSelectionModel().addListSelectionListener(action);
        shortcutTable.setFillsViewportHeight(true);
        shortcutTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        shortcutTable.setAutoCreateRowSorter(true);
        filterField.filter(shortcutTable, model);
        TableColumnModel mod = shortcutTable.getColumnModel();
        mod.getColumn(0).setCellRenderer(new ShortcutTableCellRenderer(true));
        mod.getColumn(1).setCellRenderer(new ShortcutTableCellRenderer(false));
        JScrollPane listScrollPane = new JScrollPane();
        listScrollPane.setViewportView(shortcutTable);

        gbc.weighty = 1;
        add(listScrollPane, gbc.fill(GBC.BOTH));

        // and here follows the edit area. I won't object to someone re-designing it, it looks, um, "minimalistic" ;)

        cbDefault.setAction(action);
        cbDefault.setText(tr("Use default"));
        cbShift.setAction(action);
        cbShift.setText(SHIFT); // see above for why no tr()
        cbDisable.setAction(action);
        cbDisable.setText(tr("Disable"));
        cbCtrl.setAction(action);
        cbCtrl.setText(CTRL); // see above for why no tr()
        cbAlt.setAction(action);
        cbAlt.setText(ALT); // see above for why no tr()
        tfKey.setAction(action);
        tfKey.getModel().addAllElements(keyList.values());
        cbMeta.setAction(action);
        cbMeta.setText(META); // see above for why no tr()

        JPanel shortcutEditPane = new JPanel(new GridLayout(5, 2));

        shortcutEditPane.add(cbDefault);
        shortcutEditPane.add(new JLabel());
        shortcutEditPane.add(cbShift);
        shortcutEditPane.add(cbDisable);
        shortcutEditPane.add(cbCtrl);
        shortcutEditPane.add(new JLabel(tr("Key:"), SwingConstants.LEADING));
        shortcutEditPane.add(cbAlt);
        shortcutEditPane.add(tfKey);
        shortcutEditPane.add(cbMeta);

        shortcutEditPane.add(new JLabel(tr("Attention: Use real keyboard keys only!")));

        action.actionPerformed(null); // init checkboxes

        gbc.weighty = 0;
        add(shortcutEditPane, gbc);
    }

    private JPanel buildFilterPanel() {
        // copied from PluginPreference
        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.add(filterField, GBC.eol().insets(0, 0, 0, 5).fill(GBC.HORIZONTAL));
        pnl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        return pnl;
    }

    // this allows to edit shortcuts. it:
    //  * sets the edit controls to the selected shortcut
    //  * enabled/disables the controls as needed
    //  * writes the user's changes to the shortcut
    // And after I finally had it working, I realized that those two methods
    // are playing ping-pong (politically correct: table tennis, I know) and
    // even have some duplicated code. Feel free to refactor, If you have
    // more experience with GUI coding than I have.
    private static class CbAction extends AbstractAction implements ListSelectionListener {
        private final PrefJPanel panel;

        CbAction(PrefJPanel panel) {
            this.panel = panel;
        }

        private void disableAllModifierCheckboxes() {
            panel.cbDefault.setEnabled(false);
            panel.cbDisable.setEnabled(false);
            panel.cbShift.setEnabled(false);
            panel.cbCtrl.setEnabled(false);
            panel.cbAlt.setEnabled(false);
            panel.cbMeta.setEnabled(false);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            ListSelectionModel lsm = panel.shortcutTable.getSelectionModel(); // can't use e here
            if (!lsm.isSelectionEmpty()) {
                int row = panel.shortcutTable.convertRowIndexToModel(lsm.getMinSelectionIndex());
                Shortcut sc = (Shortcut) panel.model.getValueAt(row, -1);
                panel.cbDefault.setSelected(!sc.isAssignedUser());
                panel.cbDisable.setSelected(sc.getKeyStroke() == null);
                panel.cbShift.setSelected(sc.getAssignedModifier() != -1 && (sc.getAssignedModifier() & InputEvent.SHIFT_DOWN_MASK) != 0);
                panel.cbCtrl.setSelected(sc.getAssignedModifier() != -1 && (sc.getAssignedModifier() & InputEvent.CTRL_DOWN_MASK) != 0);
                panel.cbAlt.setSelected(sc.getAssignedModifier() != -1 && (sc.getAssignedModifier() & InputEvent.ALT_DOWN_MASK) != 0);
                panel.cbMeta.setSelected(sc.getAssignedModifier() != -1 && (sc.getAssignedModifier() & InputEvent.META_DOWN_MASK) != 0);
                if (sc.getKeyStroke() != null) {
                    panel.tfKey.setSelectedItem(keyList.get(sc.getKeyStroke().getKeyCode()));
                } else {
                    panel.tfKey.setSelectedItem(keyList.get(-1));
                }
                if (!sc.isChangeable()) {
                    disableAllModifierCheckboxes();
                    panel.tfKey.setEnabled(false);
                } else {
                    panel.cbDefault.setEnabled(true);
                    actionPerformed(null);
                }
                panel.model.fireTableRowsUpdated(row, row);
            } else {
                disableAllModifierCheckboxes();
                panel.tfKey.setEnabled(false);
            }
        }

        @Override
        public void actionPerformed(java.awt.event.ActionEvent e) {
            ListSelectionModel lsm = panel.shortcutTable.getSelectionModel();
            if (lsm != null && !lsm.isSelectionEmpty()) {
                if (e != null) { // only if we've been called by a user action
                    int row = panel.shortcutTable.convertRowIndexToModel(lsm.getMinSelectionIndex());
                    Shortcut sc = (Shortcut) panel.model.getValueAt(row, -1);
                    Object selectedKey = panel.tfKey.getSelectedItem();
                    if (panel.cbDisable.isSelected()) {
                        sc.setAssignedModifier(-1);
                    } else if (selectedKey == null || "".equals(selectedKey)) {
                        sc.setAssignedModifier(KeyEvent.VK_CANCEL);
                    } else {
                        sc.setAssignedModifier(
                                (panel.cbShift.isSelected() ? InputEvent.SHIFT_DOWN_MASK : 0) |
                                (panel.cbCtrl.isSelected() ? InputEvent.CTRL_DOWN_MASK : 0) |
                                (panel.cbAlt.isSelected() ? InputEvent.ALT_DOWN_MASK : 0) |
                                (panel.cbMeta.isSelected() ? InputEvent.META_DOWN_MASK : 0)
                        );
                        for (Map.Entry<Integer, String> entry : keyList.entrySet()) {
                            if (entry.getValue().equals(selectedKey)) {
                                sc.setAssignedKey(entry.getKey());
                            }
                        }
                    }
                    sc.setAssignedUser(!panel.cbDefault.isSelected());
                    valueChanged(null);
                }
                boolean state = !panel.cbDefault.isSelected();
                panel.cbDisable.setEnabled(state);
                state = state && !panel.cbDisable.isSelected();
                panel.cbShift.setEnabled(state);
                panel.cbCtrl.setEnabled(state);
                panel.cbAlt.setEnabled(state);
                panel.cbMeta.setEnabled(state);
                panel.tfKey.setEnabled(state);
            } else {
                disableAllModifierCheckboxes();
                panel.tfKey.setEnabled(false);
            }
        }
    }
}

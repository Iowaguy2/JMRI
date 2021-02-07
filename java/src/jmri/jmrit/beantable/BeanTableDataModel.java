package jmri.jmrit.beantable;

import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.CheckForNull;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import jmri.*;
import jmri.NamedBean.DisplayOptions;
import jmri.jmrit.display.layoutEditor.LayoutBlock;
import jmri.jmrit.display.layoutEditor.LayoutBlockManager;
import jmri.swing.JTablePersistenceManager;
import jmri.util.davidflanagan.HardcopyWriter;
import jmri.util.swing.XTableColumnModel;

import jmri.util.swing.ComboBoxToolTipRenderer;

import jmri.util.table.ButtonEditor;
import jmri.util.table.ButtonRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Table data model for display of NamedBean manager contents.
 *
 * @author Bob Jacobsen Copyright (C) 2003
 * @author Dennis Miller Copyright (C) 2006
 * @param <T> the type of NamedBean supported by this model
 */
abstract public class BeanTableDataModel<T extends NamedBean> extends AbstractTableModel implements PropertyChangeListener {

    static public final int SYSNAMECOL = 0;
    static public final int USERNAMECOL = 1;
    static public final int VALUECOL = 2;
    static public final int COMMENTCOL = 3;
    static public final int DELETECOL = 4;
    static public final int NUMCOLUMN = 5;
    protected List<String> sysNameList = null;
    boolean noWarnDelete = false;
    NamedBeanHandleManager nbMan = InstanceManager.getDefault(NamedBeanHandleManager.class);
    protected final List<NamedBeanPropertyDescriptor<?>> propertyColumns;

    public BeanTableDataModel() {
        super();
        getManager().addPropertyChangeListener(this);
        propertyColumns = new ArrayList<>(getManager().getKnownBeanProperties());
        updateNameList();
    }

    protected int getPropertyColumnCount() {
        return propertyColumns.size();
    }

    protected NamedBeanPropertyDescriptor<?> getPropertyColumnDescriptor(int column) {
        int totalCount = getColumnCount();
        int propertyCount = propertyColumns.size();
        int tgt = column - (totalCount - propertyCount);
        if (tgt < 0) {
            return null;
        }
        return propertyColumns.get(tgt);
    }

    protected synchronized void updateNameList() {
        // first, remove listeners from the individual objects
        if (sysNameList != null) {
            for (String s : sysNameList) {
                // if object has been deleted, it's not here; ignore it
                T b = getBySystemName(s);
                if (b != null) {
                    b.removePropertyChangeListener(this);
                }
            }
        }
        sysNameList = getManager().getNamedBeanSet().stream().map(NamedBean::getSystemName).collect( java.util.stream.Collectors.toList() );
        // and add them back in
        for (String s : sysNameList) {
            // if object has been deleted, it's not here; ignore it
            T b = getBySystemName(s);
            if (b != null) {
                b.addPropertyChangeListener(this);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (e.getPropertyName().equals("length")) {
            // a new NamedBean is available in the manager
            updateNameList();
            log.debug("Table changed length to {}", sysNameList.size());
            fireTableDataChanged();
        } else if (matchPropertyName(e)) {
            // a value changed.  Find it, to avoid complete redraw
            if (e.getSource() instanceof NamedBean) {
                String name = ((NamedBean) e.getSource()).getSystemName();
                int row = sysNameList.indexOf(name);
                log.debug("Update cell {},{} for {}", row, VALUECOL, name);
                // since we can add columns, the entire row is marked as updated
                try {
                    fireTableRowsUpdated(row, row);
                } catch (Exception ex) {
                    log.error("Exception updating table", ex);
                }
            }
        }
    }

    /**
     * Is this property event announcing a change this table should display?
     * <p>
     * Note that events will come both from the NamedBeans and also from the
     * manager
     *
     * @param e the event to match
     * @return true if the property name is of interest, false otherwise
     */
    protected boolean matchPropertyName(PropertyChangeEvent e) {
        return (e.getPropertyName().contains("State")
                || e.getPropertyName().contains("Appearance")
                || e.getPropertyName().contains("Comment"))
                || e.getPropertyName().contains("UserName");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRowCount() {
        return sysNameList.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getColumnCount() {
        return NUMCOLUMN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getColumnName(int col) {
        switch (col) {
            case SYSNAMECOL:
                return Bundle.getMessage("ColumnSystemName"); // "System Name";
            case USERNAMECOL:
                return Bundle.getMessage("ColumnUserName");   // "User Name";
            case VALUECOL:
                return Bundle.getMessage("ColumnState");      // "State";
            case COMMENTCOL:
                return Bundle.getMessage("ColumnComment");    // "Comment";
            case DELETECOL:
                return "";
            default:
                NamedBeanPropertyDescriptor<?> desc = getPropertyColumnDescriptor(col);
                if (desc == null) {
                    return "unknown";
                }
                return desc.getColumnHeaderText();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> getColumnClass(int col) {
        switch (col) {
            case SYSNAMECOL:
                return NamedBean.class; // can't get class of T
            case USERNAMECOL:
            case COMMENTCOL:
                return String.class;
            case VALUECOL:
            case DELETECOL:
                return JButton.class;
            default:
                NamedBeanPropertyDescriptor<?> desc = getPropertyColumnDescriptor(col);
                if (desc == null) {
                    return null;
                }
                return desc.getValueClass();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCellEditable(int row, int col) {
        String uname;
        switch (col) {
            case VALUECOL:
            case COMMENTCOL:
            case DELETECOL:
                return true;
            case USERNAMECOL:
                T b = getBySystemName(sysNameList.get(row));
                uname = b.getUserName();
                return ((uname == null) || uname.isEmpty());
            default:
                NamedBeanPropertyDescriptor<?> desc = getPropertyColumnDescriptor(col);
                if (desc == null) {
                    return false;
                }
                return desc.isEditable(getBySystemName(sysNameList.get(row)));
        }
    }

    /**
     *
     * SYSNAMECOL returns the actual Bean, NOT the System Name.
     *
     * {@inheritDoc}
     */
    @Override
    public Object getValueAt(int row, int col) {
        T b;
        switch (col) {
            case SYSNAMECOL:  // slot number
                return getBySystemName(sysNameList.get(row));
            case USERNAMECOL:  // return user name
                // sometimes, the TableSorter invokes this on rows that no longer exist, so we check
                b = getBySystemName(sysNameList.get(row));
                return (b != null) ? b.getUserName() : null;
            case VALUECOL:  //
                return getValue(sysNameList.get(row));
            case COMMENTCOL:
                b = getBySystemName(sysNameList.get(row));
                return (b != null) ? b.getComment() : null;
            case DELETECOL:  //
                return Bundle.getMessage("ButtonDelete");
            default:
                NamedBeanPropertyDescriptor<?> desc = getPropertyColumnDescriptor(col);
                if (desc == null) {
                    log.error("internal state inconsistent with table requst for {} {}", row, col);
                    return null;
                }
                b = getBySystemName(sysNameList.get(row));
                Object value = b.getProperty(desc.propertyKey);
                if (desc instanceof jmri.SelectionPropertyDescriptor){
                    JComboBox<String> c = new JComboBox<>(((SelectionPropertyDescriptor) desc).getOptions());
                    c.setSelectedItem(( value!=null ? value.toString() : desc.defaultValue.toString() ));
                    c.addActionListener(this::comboBoxAction);
                    ComboBoxToolTipRenderer renderer = new ComboBoxToolTipRenderer();
                    c.setRenderer(renderer);
                    renderer.setTooltips(((SelectionPropertyDescriptor) desc).getOptionToolTips());
                    return c;
                }
                if (value == null) {
                    return desc.defaultValue;
                }
                return value;
        }
    }

    public void comboBoxAction(ActionEvent e) {
        log.debug("Combobox change");
        if (thistable != null && thistable.getCellEditor() != null) {
            thistable.getCellEditor().stopCellEditing();
        }
    }

    public int getPreferredWidth(int col) {
        switch (col) {
            case SYSNAMECOL:
                return new JTextField(5).getPreferredSize().width;
            case COMMENTCOL:
            case USERNAMECOL:
                return new JTextField(15).getPreferredSize().width; // TODO I18N using Bundle.getMessage()
            case VALUECOL: // not actually used due to the configureTable, setColumnToHoldButton, configureButton
            case DELETECOL: // not actually used due to the configureTable, setColumnToHoldButton, configureButton
                return new JTextField(22).getPreferredSize().width;
            default:
                NamedBeanPropertyDescriptor<?> desc = getPropertyColumnDescriptor(col);
                if (desc == null || desc.getColumnHeaderText() == null) {
                    log.warn("Unexpected column in getPreferredWidth: {}", col);
                    return new JTextField(8).getPreferredSize().width;
                }
                return new JTextField(desc.getColumnHeaderText()).getPreferredSize().width;
        }
    }

    abstract public String getValue(String systemName);

    abstract protected Manager<T> getManager();

    protected void setManager(@Nonnull Manager<T> man) {
    }

    abstract protected T getBySystemName(@Nonnull String name);

    abstract protected T getByUserName(@Nonnull String name);

    abstract protected void clickOn(T t);

    public int getDisplayDeleteMsg() {
        return InstanceManager.getDefault(UserPreferencesManager.class).getMultipleChoiceOption(getMasterClassName(), "deleteInUse");
    }

    public void setDisplayDeleteMsg(int boo) {
        InstanceManager.getDefault(UserPreferencesManager.class).setMultipleChoiceOption(getMasterClassName(), "deleteInUse", boo);
    }

    abstract protected String getMasterClassName();

    /**
     * {@inheritDoc}
     */
    @Override
    public void setValueAt(Object value, int row, int col) {
        switch (col) {
            case USERNAMECOL:
                // Directly changing the username should only be possible if the username was previously null or ""
                // check to see if user name already exists
                if (value.equals("")) {
                    value = null;
                } else {
                    T nB = getByUserName((String) value);
                    if (nB != null) {
                        log.error("User name is not unique {}", value);
                        String msg = Bundle.getMessage("WarningUserName", "" + value);
                        JOptionPane.showMessageDialog(null, msg,
                                Bundle.getMessage("WarningTitle"),
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }
                T nBean = getBySystemName(sysNameList.get(row));
                nBean.setUserName((String) value);
                if (nbMan.inUse(sysNameList.get(row), nBean)) {
                    String msg = Bundle.getMessage("UpdateToUserName", getBeanType(), value, sysNameList.get(row));
                    int optionPane = JOptionPane.showConfirmDialog(null,
                            msg, Bundle.getMessage("UpdateToUserNameTitle"),
                            JOptionPane.YES_NO_OPTION);
                    if (optionPane == JOptionPane.YES_OPTION) {
                        //This will update the bean reference from the systemName to the userName
                        try {
                            nbMan.updateBeanFromSystemToUser(nBean);
                        } catch (JmriException ex) {
                            //We should never get an exception here as we already check that the username is not valid
                            log.error("Impossible exception setting user name", ex);
                        }
                    }
                }
                fireTableRowsUpdated(row, row);
                break;
            case COMMENTCOL:
                getBySystemName(sysNameList.get(row)).setComment(
                        (String) value);
                fireTableRowsUpdated(row, row);
                break;
            case VALUECOL:
                // button fired, swap state
                T t = getBySystemName(sysNameList.get(row));
                clickOn(t);
                break;
            case DELETECOL:
                // button fired, delete Bean
                deleteBean(row, col);
                break;
            default:
                NamedBeanPropertyDescriptor<?> desc = getPropertyColumnDescriptor(col);
                if (desc == null) {
                    break;
                }
                if (value instanceof JComboBox) {
                    value = ((JComboBox<?>) value).getSelectedItem();
                }
                NamedBean b = getBySystemName(sysNameList.get(row));
                b.setProperty(desc.propertyKey, value);
        }
    }

    protected void deleteBean(int row, int col) {
        DeleteBeanWorker worker = new DeleteBeanWorker(getBySystemName(sysNameList.get(row)));
        worker.execute();
    }

    /**
     * Delete the bean after all the checking has been done.
     * <p>
     * Separate so that it can be easily subclassed if other functionality is
     * needed.
     *
     * @param bean NamedBean to delete
     */
    void doDelete(T bean) {
        try {
            getManager().deleteBean(bean, "DoDelete");
        } catch (PropertyVetoException e) {
            //At this stage the DoDelete shouldn't fail, as we have already done a can delete, which would trigger a veto
            log.error(e.getMessage());
        }
    }

    /**
     * Configure a table to have our standard rows and columns. This is
     * optional, in that other table formats can use this table model. But we
     * put it here to help keep it consistent. This also persists the table user
     * interface state.
     *
     * @param table {@link JTable} to configure
     */
    public void configureTable(JTable table) {
        // Property columns will be invisible at start.
        setPropertyColumnsVisible(table, false);

        table.setDefaultRenderer(JComboBox.class, new jmri.jmrit.symbolicprog.ValueRenderer());
        table.setDefaultEditor(JComboBox.class, new jmri.jmrit.symbolicprog.ValueEditor());

        // allow reordering of the columns
        table.getTableHeader().setReorderingAllowed(true);

        // have to shut off autoResizeMode to get horizontal scroll to work (JavaSwing p 541)
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // resize columns as requested
        for (int i = 0; i < table.getColumnCount(); i++) {
            int width = getPreferredWidth(i);
            table.getColumnModel().getColumn(i).setPreferredWidth(width);
        }
        table.sizeColumnsToFit(-1);

        configValueColumn(table);
        configDeleteColumn(table);

        MouseListener popupListener = new PopupListener();
        table.addMouseListener(popupListener);
        this.persistTable(table);

        thistable = table;
    }

    private JTable thistable;

    protected void configValueColumn(JTable table) {
        // have the value column hold a button
        setColumnToHoldButton(table, VALUECOL, configureButton());
    }

    public JButton configureButton() {
        // pick a large size
        JButton b = new JButton(Bundle.getMessage("BeanStateInconsistent"));
        b.putClientProperty("JComponent.sizeVariant", "small");
        b.putClientProperty("JButton.buttonType", "square");
        return b;
    }

    protected void configDeleteColumn(JTable table) {
        // have the delete column hold a button
        setColumnToHoldButton(table, DELETECOL,
                new JButton(Bundle.getMessage("ButtonDelete")));
    }

    /**
     * Service method to setup a column so that it will hold a button for its
     * values
     *
     * @param table  {@link JTable} to use
     * @param column Column to setup
     * @param sample Typical button, used for size
     */
    protected void setColumnToHoldButton(JTable table, int column, JButton sample) {
        // install a button renderer & editor
        ButtonRenderer buttonRenderer = new ButtonRenderer();
        table.setDefaultRenderer(JButton.class, buttonRenderer);
        TableCellEditor buttonEditor = new ButtonEditor(new JButton());
        table.setDefaultEditor(JButton.class, buttonEditor);
        // ensure the table rows, columns have enough room for buttons
        table.setRowHeight(sample.getPreferredSize().height);
        table.getColumnModel().getColumn(column)
                .setPreferredWidth((sample.getPreferredSize().width) + 4);
    }

    synchronized public void dispose() {
        getManager().removePropertyChangeListener(this);
        if (sysNameList != null) {
            for (String s : sysNameList) {
                T b = getBySystemName(s);
                if (b != null) {
                    b.removePropertyChangeListener(this);
                }
            }
        }
    }

    /**
     * Method to self print or print preview the table. Printed in equally sized
     * columns across the page with headings and vertical lines between each
     * column. Data is word wrapped within a column. Can handle data as strings,
     * comboboxes or booleans
     *
     * @param w the printer writer
     */
    public void printTable(HardcopyWriter w) {
        // determine the column size - evenly sized, with space between for lines
        int columnSize = (w.getCharactersPerLine() - this.getColumnCount() - 1) / this.getColumnCount();

        // Draw horizontal dividing line
        w.write(w.getCurrentLineNumber(), 0, w.getCurrentLineNumber(),
                (columnSize + 1) * this.getColumnCount());

        // print the column header labels
        String[] columnStrings = new String[this.getColumnCount()];
        // Put each column header in the array
        for (int i = 0; i < this.getColumnCount(); i++) {
            columnStrings[i] = this.getColumnName(i);
        }
        w.setFontStyle(Font.BOLD);
        printColumns(w, columnStrings, columnSize);
        w.setFontStyle(0);
        w.write(w.getCurrentLineNumber(), 0, w.getCurrentLineNumber(),
                (columnSize + 1) * this.getColumnCount());

        // now print each row of data
        // create a base string the width of the column
        StringBuilder spaces = new StringBuilder(); // NOI18N
        for (int i = 0; i < columnSize; i++) {
            spaces.append(" "); // NOI18N
        }
        for (int i = 0; i < this.getRowCount(); i++) {
            for (int j = 0; j < this.getColumnCount(); j++) {
                //check for special, non string contents
                Object value = this.getValueAt(i, j);
                if (value == null) {
                    columnStrings[j] = spaces.toString();
                } else if (value instanceof JComboBox<?>) {
                    columnStrings[j] = Objects.requireNonNull(((JComboBox<?>) value).getSelectedItem()).toString();
                } else {
                    // Boolean or String
                    columnStrings[j] = value.toString();
                }
            }
            printColumns(w, columnStrings, columnSize);
            w.write(w.getCurrentLineNumber(), 0, w.getCurrentLineNumber(),
                    (columnSize + 1) * this.getColumnCount());
        }
        w.close();
    }

    protected void printColumns(HardcopyWriter w, String[] columnStrings, int columnSize) {
        // create a base string the width of the column
        StringBuilder spaces = new StringBuilder(); // NOI18N
        for (int i = 0; i < columnSize; i++) {
            spaces.append(" "); // NOI18N
        }
        // loop through each column
        boolean complete = false;
        while (!complete) {
            StringBuilder lineString = new StringBuilder(); // NOI18N
            complete = true;
            for (int i = 0; i < columnStrings.length; i++) {
                String columnString = ""; // NOI18N
                // if the column string is too wide cut it at word boundary (valid delimiters are space, - and _)
                // use the intial part of the text,pad it with spaces and place the remainder back in the array
                // for further processing on next line
                // if column string isn't too wide, pad it to column width with spaces if needed
                if (columnStrings[i].length() > columnSize) {
                    boolean noWord = true;
                    for (int k = columnSize; k >= 1; k--) {
                        if (columnStrings[i].charAt(k - 1) == ' '
                                || columnStrings[i].charAt(k - 1) == '-'
                                || columnStrings[i].charAt(k - 1) == '_') {
                            columnString = columnStrings[i].substring(0, k)
                                    + spaces.substring(columnStrings[i].substring(0, k).length());
                            columnStrings[i] = columnStrings[i].substring(k);
                            noWord = false;
                            complete = false;
                            break;
                        }
                    }
                    if (noWord) {
                        columnString = columnStrings[i].substring(0, columnSize);
                        columnStrings[i] = columnStrings[i].substring(columnSize);
                        complete = false;
                    }

                } else {
                    columnString = columnStrings[i] + spaces.substring(columnStrings[i].length());
                    columnStrings[i] = "";
                }
                lineString.append(columnString).append(" "); // NOI18N
            }
            try {
                w.write(lineString.toString());
                //write vertical dividing lines
                for (int i = 0; i < w.getCharactersPerLine(); i = i + columnSize + 1) {
                    w.write(w.getCurrentLineNumber(), i, w.getCurrentLineNumber() + 1, i);
                }
                w.write("\n"); // NOI18N
            } catch (IOException e) {
                log.warn("error during printing: {}", e.getMessage());
            }
        }
    }

    /**
     * Create and configure a new table using the given model and row sorter.
     *
     * @param name   the name of the table
     * @param model  the data model for the table
     * @param sorter the row sorter for the table; if null, the table will not
     *               be sortable
     * @return the table
     * @throws NullPointerException if name or model is null
     */
    public JTable makeJTable(@Nonnull String name, @Nonnull TableModel model, @CheckForNull RowSorter<? extends TableModel> sorter) {
        Objects.requireNonNull(name, "the table name must be nonnull");
        Objects.requireNonNull(model, "the table model must be nonnull");
        JTable table = new JTable(model) {

            @Override
            public String getToolTipText(MouseEvent e) {
                java.awt.Point p = e.getPoint();
                int rowIndex = rowAtPoint(p);
                int colIndex = columnAtPoint(p);
                int realRowIndex = convertRowIndexToModel(rowIndex);
                int realColumnIndex = convertColumnIndexToModel(colIndex);
                return getCellToolTip(this, realRowIndex, realColumnIndex);
            }
        };
        return this.configureJTable(name, table, sorter);
    }

    /**
     * Configure a new table using the given model and row sorter.
     *
     * @param table  the table to configure
     * @param name   the table name
     * @param sorter the row sorter for the table; if null, the table will not
     *               be sortable
     * @return the table
     * @throws NullPointerException if table or the table name is null
     */
    protected JTable configureJTable(@Nonnull String name, @Nonnull JTable table, @CheckForNull RowSorter<? extends TableModel> sorter) {
        Objects.requireNonNull(table, "the table must be nonnull");
        Objects.requireNonNull(name, "the table name must be nonnull");
        table.setRowSorter(sorter);
        table.setName(name);
        table.getTableHeader().setReorderingAllowed(true);
        table.setColumnModel(new XTableColumnModel());
        table.createDefaultColumnsFromModel();
        addMouseListenerToHeader(table);
        return table;
    }

    abstract protected String getBeanType();/*{
     return "Bean";
     }*/

    /**
     * Updates the visibility settings of the property columns.
     *
     * @param table   the JTable object for the current display.
     * @param visible true to make the property columns visible, false to hide.
     */
    public void setPropertyColumnsVisible(JTable table, boolean visible) {
        XTableColumnModel columnModel = (XTableColumnModel) table.getColumnModel();
        for (int i = getColumnCount() - 1; i >= getColumnCount() - getPropertyColumnCount(); --i) {
            TableColumn column = columnModel.getColumnByModelIndex(i);
            columnModel.setColumnVisible(column, visible);
        }
    }

    protected void showPopup(MouseEvent e) {
        JTable source = (JTable) e.getSource();
        int row = source.rowAtPoint(e.getPoint());
        int column = source.columnAtPoint(e.getPoint());
        if (!source.isRowSelected(row)) {
            source.changeSelection(row, column, false, false);
        }
        final int rowindex = source.convertRowIndexToModel(row);

        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem menuItem = new JMenuItem(Bundle.getMessage("CopyName"));
        menuItem.addActionListener((ActionEvent e1) -> copyName(rowindex, 0));
        popupMenu.add(menuItem);

        menuItem = new JMenuItem(Bundle.getMessage("Rename"));
        menuItem.addActionListener((ActionEvent e1) -> renameBean(rowindex, 0));
        popupMenu.add(menuItem);

        menuItem = new JMenuItem(Bundle.getMessage("Clear"));
        menuItem.addActionListener((ActionEvent e1) -> removeName(rowindex, 0));
        popupMenu.add(menuItem);

        menuItem = new JMenuItem(Bundle.getMessage("Move"));
        menuItem.addActionListener((ActionEvent e1) -> moveBean(rowindex, 0));
        popupMenu.add(menuItem);

        menuItem = new JMenuItem(Bundle.getMessage("EditComment"));
        menuItem.addActionListener((ActionEvent e1) -> editComment(rowindex, 0));
        popupMenu.add(menuItem);

        menuItem = new JMenuItem(Bundle.getMessage("ButtonDelete"));
        menuItem.addActionListener((ActionEvent e1) -> deleteBean(rowindex, 0));
        popupMenu.add(menuItem);

        popupMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    public void copyName(int row, int column) {
        T nBean = getBySystemName(sysNameList.get(row));
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection name = new StringSelection(nBean.getUserName());
        clipboard.setContents(name, null);
    }

    /**
     * Change the bean System Name in a dialog.
     *
     * @param row table model row number of bean
     * @param column always passed in as 0, not used
     */
    public void renameBean(int row, int column) {
        T nBean = getBySystemName(sysNameList.get(row));
        String oldName = nBean.getUserName();
        JTextField _newName = new JTextField(20);
        _newName.setText(oldName);
        Object[] renameBeanOption = {Bundle.getMessage("ButtonCancel"), Bundle.getMessage("ButtonOK"), _newName};
        int retval = JOptionPane.showOptionDialog(null,
                Bundle.getMessage("RenameFrom", oldName), Bundle.getMessage("RenameTitle", getBeanType()),
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                renameBeanOption, renameBeanOption[2]);

        if (retval != 1) {
            return;
        }
        String value = _newName.getText();

        if (value.equals(oldName)) {
            //name not changed.
            return;
        } else {
            T nB = getByUserName(value);
            if (nB != null) {
                log.error("User name is not unique {}", value);
                String msg = Bundle.getMessage("WarningUserName", "" + value);
                JOptionPane.showMessageDialog(null, msg,
                        Bundle.getMessage("WarningTitle"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        if (!allowBlockNameChange("Rename", nBean, value)) return;  // NOI18N

        nBean.setUserName(value);
        fireTableRowsUpdated(row, row);
        if (!value.isEmpty()) {
            if (oldName == null || oldName.isEmpty()) {
                if (!nbMan.inUse(sysNameList.get(row), nBean)) {
                    return;
                }
                String msg = Bundle.getMessage("UpdateToUserName", getBeanType(), value, sysNameList.get(row));
                int optionPane = JOptionPane.showConfirmDialog(null,
                        msg, Bundle.getMessage("UpdateToUserNameTitle"),
                        JOptionPane.YES_NO_OPTION);
                if (optionPane == JOptionPane.YES_OPTION) {
                    //This will update the bean reference from the systemName to the userName
                    try {
                        nbMan.updateBeanFromSystemToUser(nBean);
                    } catch (JmriException ex) {
                        //We should never get an exception here as we already check that the username is not valid
                        log.error("Impossible exception renaming Bean", ex);
                    }
                }

            } else {
                nbMan.renameBean(oldName, value, nBean);
            }

        } else {
            //This will update the bean reference from the old userName to the SystemName
            nbMan.updateBeanFromUserToSystem(nBean);
        }
    }

    public void removeName(int row, int column) {
        T nBean = getBySystemName(sysNameList.get(row));
        if (!allowBlockNameChange("Remove", nBean, "")) return;  // NOI18N
        String msg = Bundle.getMessage("UpdateToSystemName", getBeanType());
        int optionPane = JOptionPane.showConfirmDialog(null,
                msg, Bundle.getMessage("UpdateToSystemNameTitle"),
                JOptionPane.YES_NO_OPTION);
        if (optionPane == JOptionPane.YES_OPTION) {
            nbMan.updateBeanFromUserToSystem(nBean);
        }
        nBean.setUserName(null);
        fireTableRowsUpdated(row, row);
    }

    /**
     * Determine whether it is safe to rename/remove a Block user name.
     * <p>The user name is used by the LayoutBlock to link to the block and
     * by Layout Editor track components to link to the layout block.
     * @param changeType This will be Remove or Rename.
     * @param bean The affected bean.  Only the Block bean is of interest.
     * @param newName For Remove this will be empty, for Rename it will be the new user name.
     * @return true to continue with the user name change.
     */
    boolean allowBlockNameChange(String changeType, T bean, String newName) {
        if (!bean.getBeanType().equals("Block")) return true;  // NOI18N

        // If there is no layout block or the block name is empty, Block rename and remove are ok without notification.
        String oldName = bean.getUserName();
        if (oldName == null) return true;
        LayoutBlock layoutBlock = jmri.InstanceManager.getDefault(LayoutBlockManager.class).getByUserName(oldName);
        if (layoutBlock == null) return true;

        // Remove is not allowed if there is a layout block
        if (changeType.equals("Remove")) {
            log.warn("Cannot remove user name for block {}", oldName);  // NOI18N
                JOptionPane.showMessageDialog(null,
                        Bundle.getMessage("BlockRemoveUserNameWarning", oldName),  // NOI18N
                        Bundle.getMessage("WarningTitle"),  // NOI18N
                        JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // Confirmation dialog
        int optionPane = JOptionPane.showConfirmDialog(null,
                Bundle.getMessage("BlockChangeUserName", oldName, newName),  // NOI18N
                Bundle.getMessage("QuestionTitle"),  // NOI18N
                JOptionPane.YES_NO_OPTION);
        return optionPane == JOptionPane.YES_OPTION;
    }

    public void moveBean(int row, int column) {
        final T t = getBySystemName(sysNameList.get(row));
        String currentName = t.getUserName();
        T oldNameBean = getBySystemName(sysNameList.get(row));

        if ((currentName == null) || currentName.isEmpty()) {
            JOptionPane.showMessageDialog(null, Bundle.getMessage("MoveDialogErrorMessage"));
            return;
        }

        JComboBox<String> box = new JComboBox<>();
        getManager().getNamedBeanSet().forEach((T b) -> {
            //Only add items that do not have a username assigned.
            String userName = b.getUserName();
            if (userName==null || userName.isEmpty()) {
                box.addItem(b.getSystemName());
            }
        });

        int retval = JOptionPane.showOptionDialog(null,
                Bundle.getMessage("MoveDialog", getBeanType(), currentName, oldNameBean.getSystemName()),
                Bundle.getMessage("MoveDialogTitle"),
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                new Object[]{Bundle.getMessage("ButtonCancel"), Bundle.getMessage("ButtonOK"), box}, null);
        log.debug("Dialog value {} selected {}:{}", retval, box.getSelectedIndex(), box.getSelectedItem());
        if (retval != 1) {
            return;
        }
        String entry = (String) box.getSelectedItem();
        assert entry != null;
        T newNameBean = getBySystemName(entry);
        if (oldNameBean != newNameBean) {
            oldNameBean.setUserName(null);
            newNameBean.setUserName(currentName);
            InstanceManager.getDefault(NamedBeanHandleManager.class).moveBean(oldNameBean, newNameBean, currentName);
            if (nbMan.inUse(newNameBean.getSystemName(), newNameBean)) {
                String msg = Bundle.getMessage("UpdateToUserName", getBeanType(), currentName, sysNameList.get(row));
                int optionPane = JOptionPane.showConfirmDialog(null, msg, Bundle.getMessage("UpdateToUserNameTitle"), JOptionPane.YES_NO_OPTION);
                if (optionPane == JOptionPane.YES_OPTION) {
                    try {
                        nbMan.updateBeanFromSystemToUser(newNameBean);
                    } catch (JmriException ex) {
                        //We should never get an exception here as we already check that the username is not valid
                        log.error("Impossible exception moving Bean", ex);
                    }
                }
            }
            fireTableRowsUpdated(row, row);
            InstanceManager.getDefault(UserPreferencesManager.class).
                    showInfoMessage(Bundle.getMessage("ReminderTitle"),
                            Bundle.getMessage("UpdateComplete", getBeanType()),
                            getMasterClassName(), "remindSaveReLoad");
        }
    }

    public void editComment(int row, int column) {
        T nBean = getBySystemName(sysNameList.get(row));
        JTextArea commentField = new JTextArea(5, 50);
        JScrollPane commentFieldScroller = new JScrollPane(commentField);
        commentField.setText(nBean.getComment());
        Object[] editCommentOption = {Bundle.getMessage("ButtonCancel"), Bundle.getMessage("ButtonUpdate")};
        int retval = JOptionPane.showOptionDialog(null,
                commentFieldScroller, Bundle.getMessage("EditComment"),
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                editCommentOption, editCommentOption[1]);
        if (retval != 1) {
            return;
        }
        nBean.setComment(commentField.getText());
   }

    /**
     * Display the comment text for the current row as a tool tip.  Most of the bean tables
     * use the standard model with comments in column 3.  The SignalMastLogic table
     * uses column 4 for the comment field.  TurnoutTableAction has its own getCellToolTip.
     * @param table The current table.
     * @param row The current row.
     * @param col The current column.
     * @return a formatted tool tip or null if there is none.
     */
    String getCellToolTip(JTable table, int row, int col) {
        String tip = null;
        if (!table.getName().contains("SignalMastLogic")) {
            int column = COMMENTCOL;
            if (table.getName().contains("SignalGroup")) column = 2;
            if (col == column) {
                T nBean = getBySystemName(sysNameList.get(row));
                if (nBean != null) {
                    tip = formatToolTip(nBean.getComment());
                }
            }
        } else {
            // SML comments are in column 4
            if (col == 4) {
                // The table does not have a "system name"
                SignalMastManager smm = InstanceManager.getDefault(SignalMastManager.class);
                SignalMast source = smm.getSignalMast((String) table.getModel().getValueAt(row, 0));
                SignalMast dest = smm.getSignalMast((String) table.getModel().getValueAt(row, 2));
                if (source != null) {
                    SignalMastLogic sml = InstanceManager.getDefault(SignalMastLogicManager.class).getSignalMastLogic(source);
                    if (sml != null && dest != null) {
                        tip = formatToolTip(sml.getComment(dest));
                    }
                }
            }
        }
        return tip;
    }

    /**
     * Format a comment field as a tool tip string. Multi line comments are supported.
     * @param comment The comment string.
     * @return a html formatted string or null if the comment is mepty.
     */
    String formatToolTip(String comment) {
        String tip = null;
        if (comment != null && !comment.isEmpty()) {
            tip = "<html>" + comment.replaceAll(System.getProperty("line.separator"), "<br>") + "</html>";
        }
        return tip;
    }

    protected void showTableHeaderPopup(MouseEvent e, JTable table) {
        JPopupMenu popupMenu = new JPopupMenu();
        XTableColumnModel tcm = (XTableColumnModel) table.getColumnModel();
        for (int i = 0; i < tcm.getColumnCount(false); i++) {
            TableColumn tc = tcm.getColumnByModelIndex(i);
            String columnName = table.getModel().getColumnName(i);
            if (columnName != null && !columnName.isEmpty()) {
                JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(table.getModel().getColumnName(i), tcm.isColumnVisible(tc));
                menuItem.addActionListener(new HeaderActionListener(tc, tcm));
                popupMenu.add(menuItem);
            }

        }
        popupMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    protected void addMouseListenerToHeader(JTable table) {
        MouseListener mouseHeaderListener = new TableHeaderListener(table);
        table.getTableHeader().addMouseListener(mouseHeaderListener);
    }

    /**
     * Persist the state of the table after first setting the table to the last
     * persisted state.
     *
     * @param table the table to persist
     * @throws NullPointerException if the name of the table is null
     */
    public void persistTable(@Nonnull JTable table) throws NullPointerException {
        InstanceManager.getOptionalDefault(JTablePersistenceManager.class).ifPresent((manager) -> {
            setColumnIdentities(table);
            manager.resetState(table); // throws NPE if table name is null
            manager.persist(table);
        });
    }

    /**
     * Stop persisting the state of the table.
     *
     * @param table the table to stop persisting
     * @throws NullPointerException if the name of the table is null
     */
    public void stopPersistingTable(@Nonnull JTable table) throws NullPointerException {
        InstanceManager.getOptionalDefault(JTablePersistenceManager.class).ifPresent((manager) -> {
            manager.stopPersisting(table); // throws NPE if table name is null
        });
    }

    /**
     * Set identities for any columns that need an identity. It is recommended
     * that all columns get a constant identity to prevent identities from being
     * subject to changes due to translation.
     * <p>
     * The default implementation sets column identities to the String
     * {@code Column#} where {@code #} is the model index for the column. Note
     * that if the TableColumnModel is a
     * {@link jmri.util.swing.XTableColumnModel}, the index includes hidden
     * columns.
     *
     * @param table the table to set identities for
     */
    protected void setColumnIdentities(JTable table) {
        Objects.requireNonNull(table.getModel(), "Table must have data model");
        Objects.requireNonNull(table.getColumnModel(), "Table must have column model");
        Enumeration<TableColumn> columns;
        if (table.getColumnModel() instanceof XTableColumnModel) {
            columns = ((XTableColumnModel) table.getColumnModel()).getColumns(false);
        } else {
            columns = table.getColumnModel().getColumns();
        }
        int i = 0;
        while (columns.hasMoreElements()) {
            TableColumn column = columns.nextElement();
            if (column.getIdentifier() == null || column.getIdentifier().toString().isEmpty()) {
                column.setIdentifier(String.format("Column%d", i));
            }
            i += 1;
        }
    }

    static class HeaderActionListener implements ActionListener {

        TableColumn tc;
        XTableColumnModel tcm;

        HeaderActionListener(TableColumn tc, XTableColumnModel tcm) {
            this.tc = tc;
            this.tcm = tcm;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JCheckBoxMenuItem check = (JCheckBoxMenuItem) e.getSource();
            //Do not allow the last column to be hidden
            if (!check.isSelected() && tcm.getColumnCount(true) == 1) {
                return;
            }
            tcm.setColumnVisible(tc, check.isSelected());
        }
    }

    class DeleteBeanWorker extends SwingWorker<Void, Void> {

        T t;

        public DeleteBeanWorker(T bean) {
            t = bean;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Void doInBackground() {
            StringBuilder message = new StringBuilder();
            try {
                getManager().deleteBean(t, "CanDelete");  // NOI18N
            } catch (PropertyVetoException e) {
                if (e.getPropertyChangeEvent().getPropertyName().equals("DoNotDelete")) { // NOI18N
                    log.warn(e.getMessage());
                    message.append(Bundle.getMessage("VetoDeleteBean", t.getBeanType(), t.getDisplayName(DisplayOptions.USERNAME_SYSTEMNAME), e.getMessage()));
                    JOptionPane.showMessageDialog(null, message.toString(),
                            Bundle.getMessage("WarningTitle"),
                            JOptionPane.ERROR_MESSAGE);
                    return null;
                }
                message.append(e.getMessage());
            }
            int count = t.getListenerRefs().size();
            log.debug("Delete with {}", count);
            if (getDisplayDeleteMsg() == 0x02 && message.toString().isEmpty()) {
                doDelete(t);
            } else {
                final JDialog dialog = new JDialog();
                dialog.setTitle(Bundle.getMessage("WarningTitle"));
                dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                JPanel container = new JPanel();
                container.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
                if (count > 0) { // warn of listeners attached before delete

                    JLabel question = new JLabel(Bundle.getMessage("DeletePrompt", t.getDisplayName(DisplayOptions.USERNAME_SYSTEMNAME)));
                    question.setAlignmentX(Component.CENTER_ALIGNMENT);
                    container.add(question);

                    ArrayList<String> listenerRefs = t.getListenerRefs();
                    if (listenerRefs.size() > 0) {
                        ArrayList<String> listeners = new ArrayList<>();
                        for (String listenerRef : listenerRefs) {
                            if (!listeners.contains(listenerRef)) {
                                listeners.add(listenerRef);
                            }
                        }

                        message.append("<br>");
                        message.append(Bundle.getMessage("ReminderInUse", count));
                        message.append("<ul>");
                        for (String listener : listeners) {
                            message.append("<li>");
                            message.append(listener);
                            message.append("</li>");
                        }
                        message.append("</ul>");

                        JEditorPane pane = new JEditorPane();
                        pane.setContentType("text/html");
                        pane.setText("<html>" + message.toString() + "</html>");
                        pane.setEditable(false);
                        JScrollPane jScrollPane = new JScrollPane(pane);
                        container.add(jScrollPane);
                    }
                } else {
                    String msg = MessageFormat.format(
                            Bundle.getMessage("DeletePrompt"), t.getSystemName());
                    JLabel question = new JLabel(msg);
                    question.setAlignmentX(Component.CENTER_ALIGNMENT);
                    container.add(question);
                }

                final JCheckBox remember = new JCheckBox(Bundle.getMessage("MessageRememberSetting"));
                remember.setFont(remember.getFont().deriveFont(10f));
                remember.setAlignmentX(Component.CENTER_ALIGNMENT);

                JButton yesButton = new JButton(Bundle.getMessage("ButtonYes"));
                JButton noButton = new JButton(Bundle.getMessage("ButtonNo"));
                JPanel button = new JPanel();
                button.setAlignmentX(Component.CENTER_ALIGNMENT);
                button.add(yesButton);
                button.add(noButton);
                container.add(button);

                noButton.addActionListener((ActionEvent e) -> {
                    //there is no point in remembering this the user will never be
                    //able to delete a bean!
                    dialog.dispose();
                });

                yesButton.addActionListener((ActionEvent e) -> {
                    if (remember.isSelected()) {
                        setDisplayDeleteMsg(0x02);
                    }
                    doDelete(t);
                    dialog.dispose();
                });
                container.add(remember);
                container.setAlignmentX(Component.CENTER_ALIGNMENT);
                container.setAlignmentY(Component.CENTER_ALIGNMENT);
                dialog.getContentPane().add(container);
                dialog.pack();
                dialog.setLocation((Toolkit.getDefaultToolkit().getScreenSize().width) / 2 - dialog.getWidth() / 2, (Toolkit.getDefaultToolkit().getScreenSize().height) / 2 - dialog.getHeight() / 2);
                dialog.setModal(true);
                dialog.setVisible(true);
            }
            return null;
        }

        /**
         * {@inheritDoc} Minimal implementation to catch and log errors
         */
        @Override
        protected void done() {
            try {
                get();  // called to get errors
            } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                log.error("Exception while deleting bean", e);
            }
        }
    }

    class PopupListener extends MouseAdapter {

        /**
         * {@inheritDoc}
         */
        @Override
        public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) {
                showPopup(e);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
                showPopup(e);
            }
        }
    }

    class PopupMenuRemoveName implements ActionListener {

        int row;

        PopupMenuRemoveName(int row) {
            this.row = row;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            deleteBean(row, 0);
        }
    }

    class TableHeaderListener extends MouseAdapter {

        JTable table;

        TableHeaderListener(JTable tbl) {
            super();
            table = tbl;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) {
                showTableHeaderPopup(e, table);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
                showTableHeaderPopup(e, table);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.isPopupTrigger()) {
                showTableHeaderPopup(e, table);
            }
        }
    }

    private final static Logger log = LoggerFactory.getLogger(BeanTableDataModel.class);

}

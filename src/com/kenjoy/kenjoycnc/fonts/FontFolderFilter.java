package com.kenjoy.kenjoycnc.fonts;

import com.kenjoy.kenjoycnc.L;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

/** The first directory below gcodeFonts is the category; files are never moved. */
public final class FontFolderFilter implements ListDataListener {
    static final String ALL = "全部", ROOT = "未分类";
    private final z local;
    private final File directory;
    private final Map files;
    private DefaultListModel source;
    private final DefaultListModel visible = new DefaultListModel();
    private final JComboBox<String> category = new JComboBox<String>();
    private final JLabel count = new JLabel();
    private boolean rebuilding;
    private boolean refreshPending;
    public void intervalAdded(ListDataEvent e) { scheduleRefresh(); }
    public void intervalRemoved(ListDataEvent e) { scheduleRefresh(); }
    public void contentsChanged(ListDataEvent e) { scheduleRefresh(); }

    FontFolderFilter(z local) { this(local, L.c()); }

    FontFolderFilter(z local, File directory) {
        this.local = local;
        this.directory = directory;
        try {
            Field field = z.class.getDeclaredField("b");
            field.setAccessible(true);
            this.files = (Map)field.get(local);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("字体文件来源与当前版本不兼容", e);
        }
        JPanel row = new JPanel(new BorderLayout(6, 3));
        JPanel choices = new JPanel(new BorderLayout(6, 0));
        choices.add(new JLabel("文件夹分类："), BorderLayout.WEST);
        category.setName("FontFolderCategory");
        choices.add(category, BorderLayout.CENTER);
        row.add(choices, BorderLayout.NORTH);
        JButton open = new JButton("打开字体目录");
        JButton refresh = new JButton("刷新");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        actions.add(open); actions.add(refresh); actions.add(count);
        row.add(actions, BorderLayout.CENTER);
        BorderLayout layout = (BorderLayout)local.getLayout();
        Component search = layout.getLayoutComponent(BorderLayout.NORTH);
        if (search != null) local.remove(search);
        JPanel header = new JPanel(new BorderLayout());
        header.add(row, BorderLayout.NORTH);
        if (search != null) header.add(search, BorderLayout.CENTER);
        local.add(header, BorderLayout.NORTH);
        category.addActionListener(e -> { if (!rebuilding) refresh(); });
        refresh.addActionListener(e -> local.load());
        open.addActionListener(e -> {
            try { Desktop.getDesktop().open(directory); }
            catch (Exception ex) { JOptionPane.showMessageDialog(local, "无法打开字体目录：" + directory); }
        });
        setSource(local.fontListModel);
    }

    void setSource(DefaultListModel model) {
        if (source != model) {
            if (source != null) source.removeListDataListener(this);
            source = model;
            source.addListDataListener(this);
        }
        local.listFonts.setModel(visible);
        refresh();
    }

    private synchronized void scheduleRefresh() {
        if (refreshPending) return;
        refreshPending = true;
        SwingUtilities.invokeLater(() -> {
            synchronized (FontFolderFilter.this) { refreshPending = false; }
            refresh();
        });
    }

    String folderOf(v font) {
        File file = (File)files.get(font);
        if (file == null) return ROOT;
        Path base = directory.toPath().toAbsolutePath().normalize();
        Path path = file.toPath().toAbsolutePath().normalize();
        if (!path.startsWith(base)) return ROOT;
        Path relative = base.relativize(path);
        return relative.getNameCount() > 1 ? relative.getName(0).toString() : ROOT;
    }

    void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) { scheduleRefresh(); return; }
        String selected = (String)category.getSelectedItem();
        Set<String> folders = new TreeSet<String>();
        File[] children = directory.listFiles(File::isDirectory);
        if (children != null) for (File child : children) folders.add(child.getName());
        for (int i=0;i<source.size();i++) folders.add(folderOf((v)source.get(i)));
        folders.remove(ROOT); folders.remove(ALL);
        rebuilding = true;
        try {
            category.removeAllItems(); category.addItem(ALL); category.addItem(ROOT);
            for (String name : folders) category.addItem(name);
            category.setSelectedItem(selected != null && (ALL.equals(selected) || ROOT.equals(selected) || folders.contains(selected)) ? selected : ALL);
        } finally { rebuilding = false; }
        selected = (String)category.getSelectedItem();
        Object previous = local.listFonts.getSelectedValue();
        visible.clear();
        for (int i=0;i<source.size();i++) {
            v font = (v)source.get(i);
            if (ALL.equals(selected) || selected.equals(folderOf(font))) visible.addElement(font);
        }
        if (previous != null && visible.contains(previous)) local.listFonts.setSelectedValue(previous, true);
        count.setText(visible.size() + " / " + source.size());
        local.listFonts.revalidate(); local.listFonts.repaint();
    }
}

package com.kenjoy.kenjoycnc.fonts;

import com.kenjoy.kenjoycnc.L;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.io.File;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
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
    private final JComboBox<String> kind = new JComboBox<String>(new String[]{ALL, "喜好字体", "中文", "英文", "符号", "其他语言"});
    private final JButton favorite = new JButton("加入喜好");
    private final Properties favorites = new Properties();
    private final File favoritesFile = new File(System.getProperty("user.home"), ".KenjoyDraw/font-favorites.properties");
    private final Map<String, String> kinds = new java.util.concurrent.ConcurrentHashMap<String, String>();
    private final JLabel count = new JLabel();
    private volatile java.util.List<String> directoryNames=java.util.Collections.emptyList();
    private java.util.concurrent.CompletableFuture<Void> directoryScan;
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
        JPanel types = new JPanel(new BorderLayout(6, 0));
        types.add(new JLabel("字体分类："), BorderLayout.WEST);
        kind.setName("FontKindCategory");
        types.add(kind, BorderLayout.CENTER);
        favorite.setName("FontFavoriteToggle");
        types.add(favorite, BorderLayout.EAST);
        row.add(types, BorderLayout.NORTH);
        JPanel choices = new JPanel(new BorderLayout(6, 0));
        choices.add(new JLabel("文件夹分类："), BorderLayout.WEST);
        category.setName("FontFolderCategory");
        choices.add(category, BorderLayout.CENTER);
        row.add(choices, BorderLayout.CENTER);
        JButton open = new JButton("打开字体目录");
        JButton refresh = new JButton("刷新");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        actions.add(open); actions.add(refresh); actions.add(count);
        row.add(actions, BorderLayout.SOUTH);
        BorderLayout layout = (BorderLayout)local.getLayout();
        Component search = layout.getLayoutComponent(BorderLayout.NORTH);
        if (search != null) local.remove(search);
        JPanel header = new JPanel(new BorderLayout());
        header.add(row, BorderLayout.NORTH);
        if (search != null) header.add(search, BorderLayout.CENTER);
        local.add(header, BorderLayout.NORTH);
        category.addActionListener(e -> { if (!rebuilding) refresh(); });
        kind.addActionListener(e -> refresh());
        local.listFonts.addListSelectionListener(e -> updateFavorite());
        favorite.addActionListener(e -> toggleFavorite());
        if (favoritesFile.isFile()) {
            try (InputStream in = new FileInputStream(favoritesFile)) { favorites.load(in); }
            catch (IOException ex) { JOptionPane.showMessageDialog(local, "读取喜好字体失败：" + ex.getMessage()); }
        }
        refresh.addActionListener(e -> local.refresh());
        open.addActionListener(e -> {
            try { Desktop.getDesktop().open(directory); }
            catch (Exception ex) { JOptionPane.showMessageDialog(local, "无法打开字体目录：" + directory); }
        });
        setSource(local.fontListModel);
        refreshDirectories();
    }

    synchronized java.util.concurrent.CompletableFuture<Void> refreshDirectories() {
        if(directoryScan!=null&&!directoryScan.isDone())return directoryScan;
        directoryScan=java.util.concurrent.CompletableFuture.runAsync(()->{
            File[] children=directory.listFiles(File::isDirectory);
            if(children==null)return;
            java.util.List<String> names=new ArrayList<>();for(File f:children)names.add(f.getName());
            directoryNames=names;scheduleRefresh();
        },FontCatalogue.WORK);
        return directoryScan;
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

    private String favoriteKey(v font) {
        // Saved selections also use the filename; keep favorites when folders move.
        File file = (File)files.get(font);
        return file != null ? file.getName() : font.f != null ? font.f : font.b;
    }

    private void updateFavorite() {
        v font = (v)local.listFonts.getSelectedValue();
        favorite.setEnabled(font != null);
        favorite.setText(font != null && favorites.containsKey(favoriteKey(font)) ? "取消喜好" : "加入喜好");
    }

    private void toggleFavorite() {
        v font = (v)local.listFonts.getSelectedValue();
        if (font == null) return;
        String key = favoriteKey(font);
        Object previous = favorites.remove(key);
        if (previous == null) favorites.setProperty(key, font.b);
        Path temporary = null;
        try {
            Files.createDirectories(favoritesFile.toPath().getParent());
            temporary = favoritesFile.toPath().resolveSibling("font-favorites-" + Long.toHexString(System.nanoTime()) + ".tmp");
            try (OutputStream out = Files.newOutputStream(temporary, java.nio.file.StandardOpenOption.CREATE_NEW)) {
                favorites.store(out, "Kvenjoy favorite fonts");
            }
            Files.move(temporary, favoritesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            if (previous == null) favorites.remove(key); else favorites.put(key, previous);
            JOptionPane.showMessageDialog(local, "保存喜好字体失败：" + ex.getMessage());
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
        refresh();
    }

    String kindOf(v font) {
        File file = (File)files.get(font);
        if (file == null) return "";
        String folder = folderOf(font);
        for (String type : new String[]{"中文", "英文", "符号", "其他语言"})
            if (folder.equals(type) || folder.startsWith(type + "-")) return type;
        String key = file.getAbsolutePath() + ":" + file.length() + ":" + file.lastModified();
        if (kinds.containsKey(key)) return kinds.get(key);
        kinds.put(key, "");
        FontCatalogue.WORK.execute(() -> {String value=detectKind(file,font);kinds.put(key,value);scheduleRefresh();});
        return "";
    }

    private String detectKind(File file,v font) {
        int chinese = 0, latin = 0, symbols = 0, other = 0;
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                int cp;
                try { cp = font.a() < 3 ? name.codePointAt(0) : Integer.parseInt(name); }
                catch (RuntimeException ex) { continue; }
                if ((cp >= 0x3400 && cp <= 0x9fff) || (cp >= 0xf900 && cp <= 0xfaff)) chinese++;
                else if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')) latin++;
                else if (Character.isLetter(cp)) other++;
                else if (cp > 127 && !Character.isWhitespace(cp) && !Character.isDigit(cp)) symbols++;
            }
        } catch (IOException ex) { return ""; }
        String value = chinese > 0 ? "中文" : other > latin ? "其他语言" : symbols > latin ? "符号" : latin > 0 ? "英文" : symbols > 0 ? "符号" : "";
        return value;
    }

    void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) { scheduleRefresh(); return; }
        String selected = (String)category.getSelectedItem();
        Set<String> folders = new TreeSet<String>(directoryNames);
        for (int i=0;i<source.size();i++) folders.add(folderOf((v)source.get(i)));
        folders.remove(ROOT); folders.remove(ALL);
        rebuilding = true;
        try {
            category.removeAllItems(); category.addItem(ALL); category.addItem(ROOT);
            for (String name : folders) category.addItem(name);
            category.setSelectedItem(selected != null && (ALL.equals(selected) || ROOT.equals(selected) || folders.contains(selected)) ? selected : ALL);
        } finally { rebuilding = false; }
        selected = (String)category.getSelectedItem();
        String selectedKind = (String)kind.getSelectedItem();
        Object previous = local.listFonts.getSelectedValue();
        visible.clear();
        for (int i=0;i<source.size();i++) {
            v font = (v)source.get(i);
            boolean matchesKind = ALL.equals(selectedKind) || ("喜好字体".equals(selectedKind)
                ? favorites.containsKey(favoriteKey(font)) : selectedKind.equals(kindOf(font)));
            if (matchesKind && (ALL.equals(selected) || selected.equals(folderOf(font)))) visible.addElement(font);
        }
        if (previous != null && visible.contains(previous)) local.listFonts.setSelectedValue(previous, true);
        count.setText(visible.size() + " / " + source.size());
        updateFavorite();
        local.listFonts.revalidate(); local.listFonts.repaint();
    }
}

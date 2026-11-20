package com.kenjoy.kenjoycnc.fonts;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Shared favorite-file format for the library and selection-font dialogs. */
public final class FontFavorites {
    private static final Object WRITE_LOCK = new Object();
    private final Path file;
    private Properties entries = new Properties();
    private final Map<String, Integer> basenameCounts = new HashMap<String, Integer>();

    public FontFavorites(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public void setFonts(Collection<File> fonts) {
        basenameCounts.clear();
        for (File font : fonts) {
            String name = font.getName().toLowerCase(Locale.ROOT);
            basenameCounts.put(name, basenameCounts.getOrDefault(name, 0) + 1);
        }
    }

    public void reload() throws IOException {
        synchronized (WRITE_LOCK) {
            entries = read();
        }
    }

    public boolean isFavorite(File font) {
        return isFavorite(entries, font);
    }

    public void toggle(File font, String label) throws IOException {
        synchronized (WRITE_LOCK) {
            // Another dialog may have changed the file since this instance last read it.
            Properties latest = read();
            boolean selected = isFavorite(latest, font);
            String pathKey = pathKey(font);
            if (uniqueBasename(font)) {
                latest.remove(font.getName());
                latest.remove(pathKey);
                // Keep unique filenames portable when the font directory moves.
                if (!selected) latest.setProperty(font.getName(), label);
            } else {
                if (selected) latest.remove(pathKey);
                else latest.setProperty(pathKey, label);
            }
            write(latest);
            entries = latest;
        }
    }

    private boolean isFavorite(Properties values, File font) {
        return values.containsKey(pathKey(font))
            || (uniqueBasename(font) && values.containsKey(font.getName()));
    }

    private boolean uniqueBasename(File font) {
        return basenameCounts.getOrDefault(font.getName().toLowerCase(Locale.ROOT), 1) == 1;
    }

    private static String pathKey(File font) {
        return "note.path:" + font.toPath().toAbsolutePath().normalize().toString()
            .replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private Properties read() throws IOException {
        Properties values = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            values.load(in);
        } catch (NoSuchFileException missing) {
            // A first favorite creates the file; other read failures must not erase it.
        } catch (IllegalArgumentException invalid) {
            throw new IOException("喜好字体文件格式无效", invalid);
        }
        return values;
    }

    private void write(Properties values) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "font-favorites-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                values.store(out, "Kvenjoy favorite fonts");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

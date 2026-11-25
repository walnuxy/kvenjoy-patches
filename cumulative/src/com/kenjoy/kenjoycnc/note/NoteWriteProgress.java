package com.kenjoy.kenjoycnc.note;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kenjoy.kenjoycnc.d.Re_a;
import com.kenjoy.kenjoycnc.g.e;
import com.kenjoy.kenjoycnc.g.f;
import com.kenjoy.kenjoycnc.machine.g;
import com.kenjoy.kenjoycnc.machine.h;
import com.kenjoy.kenjoycnc.processTabs.NoteTab;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/** Per-glyph write progress tracking, persistence across USB disconnect/restart, and slice G-code generation. */
public final class NoteWriteProgress {
    public static final int FORMAT_VERSION = 1;
    private static final String FILE_NAME = "write-progress.json";
    private static final long MAX_BYTES = 16L * 1024 * 1024;
    private static final long SAVE_THROTTLE_MS = 750L;

    public static final class GlyphEntry {
        public long id;
        public String text = "";
        public int line;
        public int column;
        public int pageOrder;
        public int startCmd;
        public int endCmd;

        public GlyphEntry copy() {
            GlyphEntry copy = new GlyphEntry();
            copy.id = id;
            copy.text = text;
            copy.line = line;
            copy.column = column;
            copy.pageOrder = pageOrder;
            copy.startCmd = startCmd;
            copy.endCmd = endCmd;
            return copy;
        }
    }

    public static final class Snapshot {
        public int formatVersion = FORMAT_VERSION;
        public String documentId = "";
        public String contentIdentity = "";
        public String textHash = "";
        public String geometryKey = "";
        public int pageIndex;
        public int pageCount;
        public float processingAngle;
        public String status = "INTERRUPTED";
        public String reason = "";
        public int startGlyphOrder;
        public int totalCommands;
        public int ackedCommands;
        public int sentCommands;
        public long lastConfirmedGlyphId = -1L;
        public long firstInFlightGlyphId = -1L;
        public long lastInFlightGlyphId = -1L;
        public long resumeGlyphId = -1L;
        public long updatedAtMillis;
        public List<GlyphEntry> glyphs = new ArrayList<GlyphEntry>();

        public Snapshot copy() {
            Snapshot copy = new Snapshot();
            copy.formatVersion = formatVersion;
            copy.documentId = documentId;
            copy.contentIdentity = contentIdentity;
            copy.textHash = textHash;
            copy.geometryKey = geometryKey;
            copy.pageIndex = pageIndex;
            copy.pageCount = pageCount;
            copy.processingAngle = processingAngle;
            copy.status = status;
            copy.reason = reason;
            copy.startGlyphOrder = startGlyphOrder;
            copy.totalCommands = totalCommands;
            copy.ackedCommands = ackedCommands;
            copy.sentCommands = sentCommands;
            copy.lastConfirmedGlyphId = lastConfirmedGlyphId;
            copy.firstInFlightGlyphId = firstInFlightGlyphId;
            copy.lastInFlightGlyphId = lastInFlightGlyphId;
            copy.resumeGlyphId = resumeGlyphId;
            copy.updatedAtMillis = updatedAtMillis;
            copy.glyphs = new ArrayList<GlyphEntry>(glyphs.size());
            for (GlyphEntry g : glyphs) {
                copy.glyphs.add(g.copy());
            }
            return copy;
        }

        public int indexOf(long id) {
            if (id <= 0L) return -1;
            for (int i = 0; i < glyphs.size(); i++) {
                if (glyphs.get(i).id == id) return i;
            }
            return -1;
        }

        public GlyphEntry entry(long id) {
            int idx = indexOf(id);
            return idx < 0 ? null : glyphs.get(idx);
        }

        public boolean hasExecutionProgress() {
            return totalCommands > 0 || ackedCommands > 0 || sentCommands > 0;
        }

        public boolean isConfirmed(long id) {
            int idx = indexOf(id);
            if (idx < 0) return false;
            if (idx < startGlyphOrder) {
                return !"RESUME_SELECTED".equals(status) || hasExecutionProgress();
            }
            GlyphEntry entry = glyphs.get(idx);
            return entry.endCmd > 0 && ackedCommands >= entry.endCmd;
        }

        public boolean isInFlight(long id) {
            int idx = indexOf(id);
            if (idx < startGlyphOrder || idx < 0) return false;
            if (isConfirmed(id)) return false;
            GlyphEntry entry = glyphs.get(idx);
            return entry.startCmd > 0 && sentCommands >= entry.startCmd;
        }

        public void updateCommandCounts(int total, int acked, int sent) {
            if (total > 0) {
                totalCommands = Math.max(totalCommands, total);
            }
            if (acked > 0) {
                ackedCommands = Math.max(ackedCommands, acked);
            }
            if (sent > 0) {
                sentCommands = Math.max(sentCommands, sent);
            }
            if (sentCommands < ackedCommands) {
                sentCommands = ackedCommands;
            }
            if (totalCommands > 0) {
                if (ackedCommands > totalCommands) ackedCommands = totalCommands;
                if (sentCommands > totalCommands) sentCommands = totalCommands;
            }
            updatedAtMillis = System.currentTimeMillis();
            recomputeGlyphPointers();
        }

        public void recomputeGlyphPointers() {
            lastConfirmedGlyphId = -1L;
            firstInFlightGlyphId = -1L;
            lastInFlightGlyphId = -1L;
            for (int i = 0; i < glyphs.size(); i++) {
                GlyphEntry entry = glyphs.get(i);
                if (isConfirmed(entry.id)) {
                    lastConfirmedGlyphId = entry.id;
                } else if (isInFlight(entry.id)) {
                    if (firstInFlightGlyphId < 0L) {
                        firstInFlightGlyphId = entry.id;
                    }
                    lastInFlightGlyphId = entry.id;
                }
            }
        }

        public long recommendedResumeGlyphId() {
            if (glyphs.isEmpty()) return -1L;
            if (firstInFlightGlyphId > 0L && indexOf(firstInFlightGlyphId) >= 0) {
                return firstInFlightGlyphId;
            }
            int begin = Math.max(0, Math.min(startGlyphOrder, glyphs.size() - 1));
            for (int i = begin; i < glyphs.size(); i++) {
                long id = glyphs.get(i).id;
                if (!isConfirmed(id)) {
                    return id;
                }
            }
            return glyphs.get(glyphs.size() - 1).id;
        }

        public long nextResumeGlyphId() {
            if (glyphs.isEmpty()) return -1L;
            long base = resumeGlyphId > 0L ? resumeGlyphId : recommendedResumeGlyphId();
            int idx = indexOf(base);
            if (idx < 0) return glyphs.get(0).id;
            if (idx + 1 < glyphs.size()) return glyphs.get(idx + 1).id;
            return -1L;
        }

        public int percent() {
            if (totalCommands <= 0) return 0;
            int pct = (int) Math.round(100.0 * Math.max(0, Math.min(totalCommands, ackedCommands)) / totalCommands);
            return Math.max(0, Math.min(100, pct));
        }

        public String progressSummary() {
            if ("RESUME_SELECTED".equals(status) && !hasExecutionProgress()) {
                return "已选择第 " + (pageIndex + 1) + " 页的续写起点";
            }
            String stateLabel;
            if ("WRITING".equals(status)) stateLabel = "正在书写";
            else if ("PAUSED".equals(status)) stateLabel = "已暂停";
            else if ("INTERRUPTED".equals(status)) stateLabel = "已中断";
            else stateLabel = "进度";
            StringBuilder out = new StringBuilder();
            out.append("第 ").append(pageIndex + 1).append(" 页").append(stateLabel);
            out.append(" (").append(percent()).append("%)");
            GlyphEntry conf = entry(lastConfirmedGlyphId);
            GlyphEntry infl = entry(firstInFlightGlyphId);
            if (conf != null) {
                out.append("：已确认至第 ").append(conf.line + 1).append(" 行第 ").append(conf.column).append(" 字「").append(conf.text).append("」");
            } else {
                out.append("：尚无已确认完成的字");
            }
            if (infl != null) {
                out.append("，当前缓冲第 ").append(infl.line + 1).append(" 行第 ").append(infl.column).append(" 字「").append(infl.text).append("」");
            }
            if (reason != null && !reason.isEmpty() && "INTERRUPTED".equals(status)) {
                out.append("（").append(reason).append("）");
            }
            return out.toString();
        }

        public String resumeTargetSummary() {
            long target = resumeGlyphId > 0L ? resumeGlyphId : recommendedResumeGlyphId();
            int idx = indexOf(target);
            if (idx < 0) {
                return "续写：未设置（整页书写）";
            }
            GlyphEntry g = glyphs.get(idx);
            int remaining = glyphs.size() - idx;
            return "续写起点：第 " + (pageIndex + 1) + " 页第 " + (g.line + 1) + " 行第 " + g.column + " 字「" + g.text + "」（剩余 " + remaining + " 字）";
        }
    }

    /** Generates G-code identical to com.kenjoy.kenjoycnc.c.c(paths, nearDst, false) while mapping each glyph's command range. */
    public static final class PageGenerator extends com.kenjoy.kenjoycnc.c.a {
        private final NoteTab owner;
        private final Snapshot snapshot;
        private final List paths;
        private final boolean trackWrite;

        public PageGenerator(NoteTab owner, Snapshot snapshot, List paths, boolean trackWrite) {
            this.owner = owner;
            this.snapshot = snapshot;
            this.paths = paths;
            this.trackWrite = trackWrite;
        }

        public Snapshot snapshot() {
            return snapshot;
        }

        @Override
        public final void a() {
            super.a();
            if (this.paths == null) {
                return;
            }
            int[] scanOffset = new int[] { 0 };
            int cmdCount = countCompletedLines(this.b, scanOffset);
            float[] pt = new float[2];
            float lastX = Float.NaN;
            float lastY = Float.NaN;
            int total = this.paths.size();
            for (int idx = 0; idx < total; idx++) {
                int beforeCmd = cmdCount;
                e segment = (e) this.paths.get(idx);
                f it = segment.h();
                while (!it.a()) {
                    byte cmd = it.a(pt);
                    if (pt[0] != lastX || pt[1] != lastY) {
                        lastX = pt[0];
                        lastY = pt[1];
                        if (cmd == 0) {
                            this.d();
                            this.a(this.b, pt, true);
                            this.c();
                        } else if (cmd == 1) {
                            this.a(this.b, pt, false);
                        }
                    }
                    it.b();
                }
                cmdCount += countCompletedLines(this.b, scanOffset);
                int glyphIdx = snapshot.startGlyphOrder + idx;
                if (glyphIdx >= 0 && glyphIdx < snapshot.glyphs.size()) {
                    GlyphEntry entry = snapshot.glyphs.get(glyphIdx);
                    if (cmdCount > beforeCmd) {
                        entry.startCmd = beforeCmd + 1;
                        entry.endCmd = cmdCount;
                    } else {
                        int fallback = Math.max(1, beforeCmd);
                        entry.startCmd = fallback;
                        entry.endCmd = fallback;
                    }
                }
                if (this.c != null) {
                    this.c.onProgress(0.6f + 0.4f * ((float) (idx + 1) / (float) total));
                }
            }
            this.b();
            cmdCount += countCompletedLines(this.b, scanOffset);
            if (scanOffset[0] < this.b.length() && hasNonWhitespace(this.b, scanOffset[0], this.b.length())) {
                cmdCount++;
            }
            snapshot.totalCommands = cmdCount;
            snapshot.updatedAtMillis = System.currentTimeMillis();
            if (trackWrite) {
                onGeneratorBuilt(owner, snapshot);
            }
        }
    }

    private static int countCompletedLines(StringBuffer buf, int[] scanOffset) {
        int count = 0;
        int len = buf.length();
        int start = scanOffset[0];
        for (int i = start; i < len; i++) {
            if (buf.charAt(i) == '\n') {
                if (hasNonWhitespace(buf, start, i)) {
                    count++;
                }
                start = i + 1;
            }
        }
        scanOffset[0] = start;
        return count;
    }

    private static boolean hasNonWhitespace(StringBuffer buf, int start, int end) {
        for (int i = start; i < end; i++) {
            if (buf.charAt(i) > ' ') {
                return true;
            }
        }
        return false;
    }

    private static final Object LOCK = new Object();
    private static final Object FILE_LOCK = new Object();
    private static final AtomicBoolean SAMPLE_PENDING = new AtomicBoolean();
    private static final ExecutorService SAMPLER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "note-write-progress-sampler");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "note-write-progress-io");
        t.setDaemon(true);
        return t;
    });

    private static NoteTab preparedTab;
    private static Snapshot preparedSnapshot;
    private static NoteTab activeTab;
    private static Snapshot activeSnapshot;
    private static long lastSaveMillis;
    private static long ioSequence;

    private NoteWriteProgress() {}

    public static Path directory() {
        return NoteRecovery.directory();
    }

    public static String textHash(String text) {
        try {
            byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                out.append(String.format("%02x", b & 255));
            }
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算文章校验摘要", e);
        }
    }

    public static void onGeneratorBuilt(NoteTab owner, Snapshot snapshot) {
        if (owner == null || snapshot == null) return;
        synchronized (LOCK) {
            preparedTab = owner;
            preparedSnapshot = snapshot.copy();
        }
    }

    public static void clearPreparedJob() {
        synchronized (LOCK) {
            preparedTab = null;
            preparedSnapshot = null;
        }
    }

    public static void clearPreparedFor(NoteTab owner) {
        synchronized (LOCK) {
            if (preparedTab == owner) {
                preparedTab = null;
                preparedSnapshot = null;
            }
        }
    }

    public static void onSenderStarted() {
        final NoteTab tab;
        final Snapshot copy;
        final long seq;
        synchronized (LOCK) {
            if (preparedSnapshot == null || preparedTab == null) return;
            activeTab = preparedTab;
            activeSnapshot = preparedSnapshot;
            preparedTab = null;
            preparedSnapshot = null;
            activeSnapshot.status = "WRITING";
            activeSnapshot.reason = "";
            activeSnapshot.updatedAtMillis = System.currentTimeMillis();
            tab = activeTab;
            copy = activeSnapshot.copy();
            lastSaveMillis = System.currentTimeMillis();
            seq = ++ioSequence;
        }
        queueSave(copy, seq);
        notifyProgress(tab, copy);
    }

    public static boolean isWriting(String documentId) {
        synchronized (LOCK) {
            return activeSnapshot != null && "WRITING".equals(activeSnapshot.status)
                && (documentId == null || Objects.equals(documentId, activeSnapshot.documentId));
        }
    }

    public static void captureSenderProgress(String interruptReason) {
        Snapshot job;
        synchronized (LOCK) { job = activeSnapshot; }
        if (job == null) return;
        boolean interrupted = interruptReason != null && !interruptReason.isEmpty();
        if (!interrupted && SwingUtilities.isEventDispatchThread()) {
            if (SAMPLE_PENDING.compareAndSet(false, true)) {
                SAMPLER.execute(() -> {
                    try { captureSenderProgress(job, null, true); }
                    finally { SAMPLE_PENDING.set(false); }
                });
            }
            return;
        }
        // Terminal UI events must mark the task interrupted without waiting on a
        // blocked serial write. The sender's own progress events provide the last sample.
        captureSenderProgress(job, interruptReason, !SwingUtilities.isEventDispatchThread());
    }

    private static void captureSenderProgress(Snapshot job, String interruptReason, boolean sampleSender) {
        final NoteTab tab;
        final Snapshot snapshotCopy;
        final boolean interrupted = interruptReason != null && !interruptReason.isEmpty();
        final boolean shouldSaveNow;
        final long seq;
        Snapshot sample = null;
        if (sampleSender) {
            synchronized (LOCK) {
                if (activeSnapshot != job) return;
                sample = new Snapshot();
                sample.status = job.status;
                sample.updateCommandCounts(job.totalCommands, job.ackedCommands, job.sentCommands);
            }
            // A sender callback can run while holding its own monitor. Never hold
            // LOCK while acquiring that monitor, or block the EDT on a callback.
            readSenderState(sample);
        }
        synchronized (LOCK) {
            if (activeSnapshot != job) return;
            if (sample != null) {
                job.updateCommandCounts(sample.totalCommands, sample.ackedCommands, sample.sentCommands);
                job.status = sample.status;
            }
            if (interrupted) {
                activeSnapshot.status = "INTERRUPTED";
                activeSnapshot.reason = interruptReason;
                activeSnapshot.resumeGlyphId = activeSnapshot.recommendedResumeGlyphId();
            }
            tab = activeTab;
            snapshotCopy = activeSnapshot.copy();
            if (interrupted) {
                activeTab = null;
                activeSnapshot = null;
                lastSaveMillis = System.currentTimeMillis();
                seq = ++ioSequence;
                shouldSaveNow = true;
            } else {
                long now = System.currentTimeMillis();
                if (now - lastSaveMillis >= SAVE_THROTTLE_MS) {
                    lastSaveMillis = now;
                    seq = ++ioSequence;
                    shouldSaveNow = true;
                } else {
                    seq = ioSequence;
                    shouldSaveNow = false;
                }
            }
        }
        if (interrupted && !SwingUtilities.isEventDispatchThread()) {
            try {
                synchronized (FILE_LOCK) {
                    if (seq == latestIoSequence()) {
                        save(directory(), snapshotCopy);
                    }
                }
            } catch (IOException ignored) {
                // Best effort during abrupt USB disconnect; caller must still finish disconnecting.
            }
            notifyInterrupted(tab, snapshotCopy);
        } else if (interrupted) {
            queueSave(snapshotCopy, seq);
            notifyInterrupted(tab, snapshotCopy);
        } else {
            if (shouldSaveNow) {
                queueSave(snapshotCopy, seq);
            }
            if (tab != null) SwingUtilities.invokeLater(() -> {
                synchronized (LOCK) { if (activeSnapshot != job) return; }
                tab.onWriteJobProgress(snapshotCopy);
            });
        }
    }

    public static void completeActiveJob() {
        final NoteTab tab;
        final long seq;
        synchronized (LOCK) {
            if (activeSnapshot == null) return;
            tab = activeTab;
            activeTab = null;
            activeSnapshot = null;
            seq = ++ioSequence;
        }
        clearSavedAt(seq);
        if (tab != null) {
            Runnable action = () -> tab.onWriteJobCompleted();
            if (SwingUtilities.isEventDispatchThread()) action.run();
            else SwingUtilities.invokeLater(action);
        }
    }

    public static void saveResumeSelection(Snapshot snapshot) {
        if (snapshot == null) return;
        Snapshot copy = snapshot.copy();
        final long seq;
        synchronized (LOCK) {
            seq = ++ioSequence;
        }
        queueSave(copy, seq);
    }

    public static void clearSaved() {
        final long seq;
        synchronized (LOCK) {
            seq = ++ioSequence;
        }
        clearSavedAt(seq);
    }

    private static long latestIoSequence() {
        synchronized (LOCK) {
            return ioSequence;
        }
    }

    private static void queueSave(final Snapshot snapshot, final long seq) {
        IO_EXECUTOR.execute(() -> {
            synchronized (FILE_LOCK) {
                if (seq != latestIoSequence()) return;
                try {
                    save(directory(), snapshot);
                } catch (IOException ignored) {
                }
            }
        });
    }

    private static void clearSavedAt(final long seq) {
        synchronized (FILE_LOCK) {
            if (seq != latestIoSequence()) return;
            try {
                clear(directory());
            } catch (IOException ignored) {
            }
        }
    }

    private static void notifyProgress(final NoteTab tab, final Snapshot snapshot) {
        if (tab == null || snapshot == null) return;
        Runnable action = () -> tab.onWriteJobProgress(snapshot);
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    private static void notifyInterrupted(final NoteTab tab, final Snapshot snapshot) {
        if (tab == null || snapshot == null) return;
        Runnable action = () -> tab.onWriteJobInterrupted(snapshot);
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    private static void readSenderState(Snapshot snapshot) {
        try {
            g manager = g.a();
            if (manager == null) return;
            h sender = manager.i();
            if (sender == null) return;
            synchronized (sender) {
                List all = (List) senderField(sender, "f");
                Collection queued = (Collection) senderField(sender, "c");
                Collection pausedList = (Collection) senderField(sender, "k");
                Collection awaiting = (Collection) senderField(sender, "j");
                boolean running = Boolean.TRUE.equals(senderField(sender, "d"));
                boolean paused = Boolean.TRUE.equals(senderField(sender, "h"));
                if (all == null || all.isEmpty()) {
                    return;
                }
                int total = all.size();
                if (!running && (queued == null || queued.isEmpty())
                    && (pausedList == null || pausedList.isEmpty())
                    && (awaiting == null || awaiting.isEmpty())
                    && snapshot.sentCommands == 0) {
                    return;
                }
                if (!"INTERRUPTED".equals(snapshot.status)) {
                    snapshot.status = paused ? "PAUSED" : "WRITING";
                }
                int minUnsent = Integer.MAX_VALUE;
                minUnsent = Math.min(minUnsent, minPositiveCommandId(queued));
                minUnsent = Math.min(minUnsent, minPositiveCommandId(pausedList));
                int minAwaiting = minPositiveCommandId(awaiting);
                int minUnacked = Math.min(minUnsent, minAwaiting);
                int sent = minUnsent == Integer.MAX_VALUE ? total : Math.max(0, minUnsent - 1);
                int acked = minUnacked == Integer.MAX_VALUE ? total : Math.max(0, minUnacked - 1);
                snapshot.updateCommandCounts(total, acked, sent);
            }
        } catch (Throwable ignored) {
            // Preserve last known counts if sender state cannot be read.
        }
    }

    private static int minPositiveCommandId(Collection items) {
        if (items == null || items.isEmpty()) return Integer.MAX_VALUE;
        int min = Integer.MAX_VALUE;
        for (Object item : items) {
            if (item instanceof Re_a) {
                int id = ((Re_a) item).b();
                if (id > 0 && id < min) {
                    min = id;
                }
            }
        }
        return min;
    }

    private static Object senderField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    public static synchronized Snapshot load() throws IOException {
        return load(directory());
    }

    public static synchronized void save(Snapshot snapshot) throws IOException {
        save(directory(), snapshot);
    }

    public static synchronized void clear() throws IOException {
        clear(directory());
    }

    public static synchronized Snapshot load(Path directory) throws IOException {
        Path file = directory.resolve(FILE_NAME);
        if (!Files.exists(file)) return null;
        try {
            String json = new String(readBytes(file, MAX_BYTES), StandardCharsets.UTF_8);
            Snapshot snapshot = new Gson().fromJson(json, Snapshot.class);
            validate(snapshot);
            snapshot.recomputeGlyphPointers();
            return snapshot;
        } catch (RuntimeException error) {
            throw new IOException("书写进度文件无效，已保留原文件：" + file, error);
        }
    }

    public static synchronized void save(Path directory, Snapshot snapshot) throws IOException {
        try {
            validate(snapshot);
            byte[] data = new GsonBuilder().create().toJson(snapshot).getBytes(StandardCharsets.UTF_8);
            if (data.length > MAX_BYTES) {
                throw new IOException("书写进度超过大小上限");
            }
            atomicWrite(directory.resolve(FILE_NAME), data);
        } catch (RuntimeException error) {
            throw new IOException("无法保存书写进度：" + error.getMessage(), error);
        }
    }

    public static synchronized void clear(Path directory) throws IOException {
        Files.deleteIfExists(directory.resolve(FILE_NAME));
    }

    private static void validate(Snapshot snapshot) {
        if (snapshot == null || snapshot.formatVersion != FORMAT_VERSION || snapshot.documentId == null || snapshot.documentId.isEmpty()) {
            throw new IllegalArgumentException("书写进度版本或文章标识无效");
        }
        if (snapshot.pageIndex < 0 || snapshot.pageCount < 0 || !Float.isFinite(snapshot.processingAngle)
            || snapshot.startGlyphOrder < 0 || snapshot.totalCommands < 0 || snapshot.ackedCommands < 0 || snapshot.sentCommands < 0) {
            throw new IllegalArgumentException("书写进度计数无效");
        }
        if (snapshot.glyphs == null) {
            throw new IllegalArgumentException("书写进度缺少字形清单");
        }
        for (GlyphEntry entry : snapshot.glyphs) {
            if (entry == null || entry.id <= 0L || entry.text == null || entry.line < 0 || entry.column < 0
                || entry.pageOrder < 0 || entry.startCmd < 0 || entry.endCmd < 0) {
                throw new IllegalArgumentException("书写进度字形记录无效");
            }
        }
    }

    private static byte[] readBytes(Path file, long limit) throws IOException {
        if (Files.size(file) > limit) {
            throw new IOException("书写进度文件超过大小上限：" + file.getFileName());
        }
        try (InputStream in = Files.newInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            long total = 0L;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > limit) throw new IOException("书写进度文件超过大小上限");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static void atomicWrite(Path target, byte[] data) throws IOException {
        Path file = target.toAbsolutePath();
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, file.getFileName() + "-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

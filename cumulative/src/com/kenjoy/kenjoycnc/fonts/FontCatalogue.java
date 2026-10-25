package com.kenjoy.kenjoycnc.fonts;

import com.kenjoy.kenjoycnc.L;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Immutable catalog snapshots; unchanged entries retain their independently lazy caches. */
public final class FontCatalogue {
    public static final ExecutorService WORK = Executors.newFixedThreadPool(2, r->{Thread t=new Thread(r,"font-library");t.setDaemon(true);return t;});
    private static final Map<Path,FontCatalogue> CATALOGUES=new HashMap<>();
    public static synchronized FontCatalogue at(File root){Path p=root.toPath().toAbsolutePath().normalize();FontCatalogue c=CATALOGUES.get(p);if(c==null){c=new FontCatalogue(p);CATALOGUES.put(p,c);}return c;}
    public static final class Entry {
        public final Path path;public final long size,modified;public final v header;
        private volatile v preview;private volatile boolean previewRequested;public volatile String previewError;
        Entry(Path p,long s,long m,v f){path=p;size=s;modified=m;header=f;}
        public boolean unchanged(){try{return Files.size(path)==size&&Files.getLastModifiedTime(path).toMillis()==modified;}catch(IOException e){return false;}}
        public v preview(){return preview;}
        public synchronized void preview(Runnable ready){if(previewRequested)return;previewRequested=true;WORK.execute(()->{long n=System.nanoTime();v f=new v();try(InputStream in=Files.newInputStream(path)){if(!unchanged())throw new IOException("文件已变化，请刷新");f.b(in);f.f=header.f;if(f.d()!=null)f.d().a();if(!unchanged())throw new IOException("文件已变化，请刷新");preview=f;}catch(Exception e){f.f();previewError=e.getMessage();}finally{if(Boolean.getBoolean("kvenjoy.font.timings"))System.out.println("FONT_PREVIEW ns="+(System.nanoTime()-n));javax.swing.SwingUtilities.invokeLater(ready);}});}
    }
    public static final class Snapshot {
        public final List<Entry> entries;public final List<String> errors;public final long scanNanos,parseNanos;public final int reused;
        Snapshot(List<Entry> e,List<String> errors,long scan,long parse,int reused){entries=Collections.unmodifiableList(e);this.errors=Collections.unmodifiableList(errors);scanNanos=scan;parseNanos=parse;this.reused=reused;}
    }
    private final Path root;private volatile Snapshot current;private CompletableFuture<Snapshot> pending;
    private FontCatalogue(Path root){this.root=root;}
    public Snapshot current(){return current;}
    public synchronized CompletableFuture<Snapshot> refresh(){if(pending!=null)return pending;CompletableFuture<Snapshot> result=new CompletableFuture<>();pending=result;WORK.execute(()->{try{Snapshot next=scan();synchronized(this){current=next;if(pending==result)pending=null;}result.complete(next);}catch(Exception e){synchronized(this){if(pending==result)pending=null;}result.completeExceptionally(e);}});return result;}
    private Snapshot scan()throws Exception{
        long n=System.nanoTime();List<Path> paths=new ArrayList<>();try(java.util.stream.Stream<Path>s=Files.walk(root)){s.filter(p->Files.isRegularFile(p)&&p.toString().toLowerCase(Locale.ROOT).endsWith(".gfont")).sorted().forEach(paths::add);}long scan=System.nanoTime()-n;n=System.nanoTime();
        Map<Path,Entry> prior=new HashMap<>();if(current!=null)for(Entry e:current.entries)prior.put(e.path,e);
        List<Entry> next=new ArrayList<>();List<String> errors=new ArrayList<>();int reused=0;
        for(Path p:paths){Entry old=prior.get(p);if(old!=null&&old.unchanged()){next.add(old);reused++;continue;}
            v f=new v();try{long size=Files.size(p),mtime=Files.getLastModifiedTime(p).toMillis();try(InputStream in=Files.newInputStream(p)){f.a(in);}f.f=p.getFileName().toString();Entry e=new Entry(p,size,mtime,f);if(!e.unchanged())throw new IOException("扫描时文件发生变化");if(L.a(f))next.add(e);}catch(Exception e){f.f();errors.add(root.relativize(p)+": "+e.getClass().getSimpleName());}}
        return new Snapshot(next,errors,scan,System.nanoTime()-n,reused);
    }
}

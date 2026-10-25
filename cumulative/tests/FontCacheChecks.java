package com.kenjoy.kenjoycnc.fonts;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
/** Same-library timing plus isolated mutation checks. Never changes installed fonts. */
public final class FontCacheChecks {
    static int count;static void check(String name,boolean ok){if(!ok)throw new AssertionError(name);count++;System.out.println("FONT_CACHE_CHECK "+name);}
    public static void main(String[] args)throws Exception{
        com.kenjoy.kenjoycnc.o.c.c="kvenjoy";
        FontCatalogue cache=FontCatalogue.at(Paths.get(args[0]).toFile());FontCatalogue.Snapshot first=cache.refresh().get(30,TimeUnit.SECONDS);
        check("catalog has fonts",!first.entries.isEmpty());
        for(int i=0;i<3;i++){long start=System.nanoTime();FontCatalogue.Snapshot s=cache.refresh().get();double total=(System.nanoTime()-start)/1e6;System.out.printf(Locale.ROOT,"optimized run=%d files=%d scan_ms=%.2f parse_ms=%.2f total_ms=%.2f reused=%d%n",i,s.entries.size(),s.scanNanos/1e6,s.parseNanos/1e6,total,s.reused);check("unchanged header reused "+i,s.entries.get(0)==first.entries.get(0)&&s.reused==s.entries.size());}
        Path dir=Paths.get(System.getProperty("user.home"),"kvenjoy-font-cache-check-"+Long.toHexString(System.nanoTime()));Files.createDirectories(dir);Path a=dir.resolve("one.gfont");Files.copy(first.entries.get(0).path,a);
        FontCatalogue test=FontCatalogue.at(dir.toFile());FontCatalogue.Snapshot initial=test.refresh().get();
        Path b=dir.resolve("two.gfont");Files.copy(a,b);FontCatalogue.Snapshot added=test.refresh().get();check("new file discovered",added.entries.size()==2&&added.entries.get(0)==initial.entries.get(0));
        Files.setLastModifiedTime(a,java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(a).toMillis()+2000));FontCatalogue.Snapshot modified=test.refresh().get();check("modified file reloaded only once",modified.entries.get(0)!=added.entries.get(0)&&modified.entries.get(1)==added.entries.get(1));
        Files.delete(b);FontCatalogue.Snapshot removed=test.refresh().get();check("deleted file removed",removed.entries.size()==1);
        Files.write(dir.resolve("broken.gfont"),new byte[]{0,0,0,99});FontCatalogue.Snapshot broken=test.refresh().get();check("corrupt file isolated and reported",broken.entries.size()==1&&broken.errors.size()==1);
        Path moved=dir.resolveSibling(dir.getFileName()+"-moved");Files.move(dir,moved);try{test.refresh().get();throw new AssertionError("missing directory should fail");}catch(ExecutionException expected){check("failed scan retains prior snapshot",test.current()==broken);}finally{Files.move(moved,dir);}
        CountDownLatch preview=new CountDownLatch(1);first.entries.get(0).preview(preview::countDown);check("lazy preview completes",preview.await(10,TimeUnit.SECONDS)&&first.entries.get(0).preview()!=null);
        check("warm refresh retains preview",cache.refresh().get().entries.get(0).preview()==first.entries.get(0).preview());
        // Both refresh callers share one scan while its executor is deliberately occupied.
        CountDownLatch hold=new CountDownLatch(1),started=new CountDownLatch(2);for(int i=0;i<2;i++)FontCatalogue.WORK.execute(()->{started.countDown();try{hold.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});started.await();CompletableFuture<FontCatalogue.Snapshot> one=cache.refresh(),two=cache.refresh();check("concurrent refresh coalesced",one==two);hold.countDown();one.get();
        Files.delete(dir.resolve("broken.gfont"));Files.delete(a);Files.delete(dir);
        System.out.println("COMPLETE fontCacheChecks="+count);System.exit(0);
    }
}

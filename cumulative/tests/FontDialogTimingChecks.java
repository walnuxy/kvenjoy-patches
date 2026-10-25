package com.kenjoy.kenjoycnc.fonts;
import java.lang.reflect.*;
import javax.swing.*;
import java.util.concurrent.*;
/** Uses the same original dialog entry on baseline and candidate classpaths. */
public final class FontDialogTimingChecks {
    static Object get(Object o,String n)throws Exception{Class<?> c=o.getClass();while(c!=null){try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
    public static void main(String[] args)throws Exception{
        Thread.setDefaultUncaughtExceptionHandler((t,e)->{e.printStackTrace();System.exit(1);});
        com.kenjoy.kenjoycnc.o.c.c="kvenjoy";
        com.kenjoy.kenjoycnc.data.MachineData machine=new com.kenjoy.kenjoycnc.data.MachineData();machine.name="FontDialogChecks";com.kenjoy.kenjoycnc.o.c.e=machine;
        r[] d=new r[1];long start=System.nanoTime();SwingUtilities.invokeAndWait(()->{d[0]=new r();d[0].setModal(false);d[0].setVisible(true);});
        System.out.printf(java.util.Locale.ROOT,"first_dialog_operable_ms=%.2f%n",(System.nanoTime()-start)/1e6);
        Object local=get(d[0],"l");long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while((Boolean)get(local,"loading")){if(System.nanoTime()>until)throw new AssertionError("loading timeout");Thread.sleep(20);}
        for(int i=0;i<3;i++){
            SwingUtilities.invokeAndWait(()->{try{Method close=r.class.getDeclaredMethod("b");close.setAccessible(true);close.invoke(d[0]);d[0].setVisible(false);}catch(Exception e){throw new RuntimeException(e);}});
            start=System.nanoTime();SwingUtilities.invokeAndWait(()->d[0].setVisible(true));double ms=(System.nanoTime()-start)/1e6;System.out.printf(java.util.Locale.ROOT,"reopen=%d operable_ms=%.2f%n",i,ms);
            if(args.length>0&&args[0].equals("optimized")&&ms>500)throw new AssertionError("warm dialog exceeds 500ms");
            long loadedUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while((Boolean)get(local,"loading")){if(System.nanoTime()>loadedUntil)throw new AssertionError("reload timeout");Thread.sleep(10);}
            SwingUtilities.invokeAndWait(()->{});System.out.printf(java.util.Locale.ROOT,"reopen=%d list_ready_ms=%.2f%n",i,(System.nanoTime()-start)/1e6);
            Thread.sleep(100);
        }
        SwingUtilities.invokeAndWait(()->d[0].dispose());System.out.println("COMPLETE fontDialogTiming");System.exit(0);
    }
}

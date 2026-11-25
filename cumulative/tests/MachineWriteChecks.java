import java.awt.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import com.kenjoy.uitrial.*;

/** Exercises the real write button and interrupted-task recovery without opening a port. */
public final class MachineWriteChecks {
    static Field field(Object owner,String name)throws Exception{
        for(Class<?> t=owner.getClass();t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static Object get(Object owner,String name)throws Exception{return field(owner,name).get(owner);}
    static void set(Object owner,String name,Object value)throws Exception{field(owner,name).set(owner,value);}
    static Method method(Class<?> type,String name,Class<?>...args)throws Exception{Method m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    static void check(String name,boolean value)throws Exception{ProductionChecks.check("write recovery: "+name,value);}
    static void pauseStatusCheck(String name,String expected,String actual){
        if(!expected.equals(actual))throw new AssertionError(name+": expected <"+expected+"> but was <"+actual+">");
    }
    static void runPauseStatusChecks()throws Exception{
        Method status=method(MachinePanelWorkflow.class,"activeTaskStatus",String.class,boolean.class,boolean.class);
        pauseStatusCheck("paused source stays visibly paused during a long response wait","已暂停：现场任务",(String)status.invoke(null,"现场任务",true,true));
        pauseStatusCheck("response timeout remains visible while active","等待设备应答较久；可终止任务后清除状态",(String)status.invoke(null,"现场任务",false,true));
        pauseStatusCheck("ordinary active source keeps its writing label","正在书写：现场任务",(String)status.invoke(null,"现场任务",false,false));
        String hint=(String)method(MachinePanelWorkflow.class,"activeTaskProgressHint",boolean.class).invoke(null,true);
        if(!hint.contains("不代表机器已完成")||!hint.contains("完成事件"))throw new AssertionError("paused ETA tooltip must distinguish queue estimates from completion: "+hint);
    }
    static javax.swing.Timer dialogs(java.util.List<String> messages,int answer){
        javax.swing.Timer timer=new javax.swing.Timer(20,event->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isShowing()){
            JOptionPane pane=findPane((Container)w);if(pane!=null){messages.add(String.valueOf(pane.getMessage()));pane.setValue(answer);w.dispose();}
        }});timer.start();return timer;
    }
    static JOptionPane findPane(Container c){for(Component child:c.getComponents()){if(child instanceof JOptionPane)return (JOptionPane)child;if(child instanceof Container){JOptionPane p=findPane((Container)child);if(p!=null)return p;}}return null;}
    private static void awaitUi(BooleanSupplier done){
        if(done.getAsBoolean())return;
        SecondaryLoop loop=Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        javax.swing.Timer timer=new javax.swing.Timer(25,e->{if(done.getAsBoolean()||System.nanoTime()>=deadline)loop.exit();});
        timer.start();try{loop.enter();}finally{timer.stop();}
        if(!done.getAsBoolean())throw new AssertionError("UI action did not complete");
    }
    private static boolean flag(Object owner,String name){try{return Boolean.TRUE.equals(get(owner,name));}catch(Exception error){throw new AssertionError(error);}}
    public static void run(Object app)throws Exception{
        runPauseStatusChecks();
        EventQueue.invokeAndWait(()->{try{
            JFrame frame=(JFrame)app.getClass().getMethod("f").invoke(app);
            Object flow=frame.getRootPane().getClientProperty("modernization.machinePanel"),device=UiStyle.field(app,"y");
            Object manager=Class.forName("com.kenjoy.kenjoycnc.machine.g").getMethod("a").invoke(null),firmware=manager.getClass().getMethod("e").invoke(manager),sender=manager.getClass().getMethod("i").invoke(manager);
            Object transport=manager.getClass().getMethod("g").invoke(manager);
            check("no serial port is open",Boolean.FALSE.equals(transport.getClass().getMethod("b").invoke(transport)));
            JButton write=(JButton)get(device,"m");JLabel status=(JLabel)get(device,"r"),source=(JLabel)get(device,"E");
            Object settings=Class.forName("com.kenjoy.kenjoycnc.model.Settings").getMethod("getInst").invoke(null);
            Object priorExternal=get(device,"F"),priorConnected=get(firmware,"d"),priorUpdate=get(settings,"updateStatus");String priorStatus=status.getText();
            Path external=Paths.get(System.getProperty("trial.evidence"),"write-fixture.gcode");Files.write(external,"G1 X1 Y1 F1000\n".getBytes("UTF-8"));
            java.util.List<String> messages=new ArrayList<String>();javax.swing.Timer responder=dialogs(messages,JOptionPane.OK_OPTION);
            try{
                set(device,"F",external.toFile());set(firmware,"d",true);set(settings,"updateStatus",false);
                write.setEnabled(true);write.doClick(0);
                check("stale connected cache produces an explicit reconnect message",messages.toString().contains("连接"));
                check("failed preflight never freezes or submits a task",!MachinePanelWorkflow.sourceLocked(app)&&!Boolean.TRUE.equals(sender.getClass().getMethod("i").invoke(sender)));
                MachinePanelWorkflow.refresh(app);check("closed port cannot leave write enabled",!write.isEnabled());
            }finally{responder.stop();set(device,"F",priorExternal);set(firmware,"d",priorConnected);set(settings,"updateStatus",priorUpdate);status.setText(priorStatus);}
            Method begin=method(MachinePanelWorkflow.class,"beginTask",Object.class),clear=method(MachinePanelWorkflow.class,"clearTask",Object.class);
            Object oldWorker=new Object();begin.invoke(null,device);MachinePanelWorkflow.bindGenerator(device,oldWorker);MachinePanelWorkflow.autoZeroHandoff(device,oldWorker);
            set(flow,"activeSource","中断任务");set(flow,"writeFailureLatch",true);set(flow,"handoffFailureMessage","任务已中断");
            ((JComboBox)get(app,"e")).setEnabled(false);set(flow,"modeLocked",true);set(flow,"modesEnabledBefore",true);set(sender,"d",true);
            messages.clear();responder=dialogs(messages,JOptionPane.NO_OPTION);
            try{MachinePanelWorkflow.refresh(app);check("interrupted task offers a usable recovery action",write.isEnabled()&&write.getText().contains("清除"));write.doClick(0);
                check("canceling recovery preserves the old task lock",MachinePanelWorkflow.sourceLocked(app)&&Boolean.TRUE.equals(sender.getClass().getMethod("i").invoke(sender)));
            }finally{responder.stop();}
            CountDownLatch senderHeld=new CountDownLatch(1),senderReleased=new CountDownLatch(1);
            Thread blockedWriter=new Thread(()->{synchronized(sender){senderHeld.countDown();try{senderReleased.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}},"synthetic-blocked-sender");
            blockedWriter.setDaemon(true);blockedWriter.start();
            check("sender lock fixture is active",senderHeld.await(3,TimeUnit.SECONDS));
            Class<?> progress=Class.forName("com.kenjoy.kenjoycnc.note.NoteWriteProgress");
            Field active=progress.getDeclaredField("activeSnapshot");active.setAccessible(true);
            Field saved=progress.getDeclaredField("lastSaveMillis");saved.setAccessible(true);
            active.set(null,Class.forName(progress.getName()+"$Snapshot").newInstance());saved.setLong(null,System.currentTimeMillis());
            try{
                progress.getMethod("captureSenderProgress",String.class).invoke(null,(Object)null);
                check("note progress sampling returns while the serial monitor is held",blockedWriter.isAlive());
            }finally{active.set(null,null);}
            responder=dialogs(messages,JOptionPane.YES_OPTION);
            try{write.doClick(0);
                check("confirmed recovery returns while serial writer still holds sender lock",flag(flow,"recovering")&&blockedWriter.isAlive()&&MachinePanelWorkflow.sourceLocked(app));
                check("recovery disables repeated write and keeps late callback canceled",!write.isEnabled()&&!MachinePanelWorkflow.allowAutoZeroSend(oldWorker));
                senderReleased.countDown();
                awaitUi(()->!flag(flow,"recovering"));
                check("confirmed recovery releases source and mode selection",!MachinePanelWorkflow.sourceLocked(app)&&((JComboBox)get(app,"e")).isEnabled());
                check("recovery discards the local sender without starting a new job",Boolean.FALSE.equals(sender.getClass().getMethod("i").invoke(sender))&&!write.isEnabled());
                check("late auto-zero completion cannot restart the discarded task",!MachinePanelWorkflow.allowAutoZeroSend(oldWorker));
                check("recovery explains that the starting position needs checking",source.getToolTipText().contains("起写位置"));
            }finally{senderReleased.countDown();responder.stop();}
            Object worker=new Object();begin.invoke(null,device);MachinePanelWorkflow.bindGenerator(device,worker);MachinePanelWorkflow.generationStarted(device,worker);
            set(flow,"activeSource","退出中的生成器");set(flow,"writeFailureLatch",true);MachinePanelWorkflow.refresh(app);
            check("recovery waits for the canceled generator to exit",!write.isEnabled());
            Field generators=MachinePanelWorkflow.class.getDeclaredField("ACTIVE_GENERATORS");generators.setAccessible(true);((Map)generators.get(null)).remove(device,worker);clear.invoke(null,device);
            set(flow,"activeSource",null);set(flow,"writeFailureLatch",false);set(flow,"handoffFailureMessage",null);
            set(firmware,"d",true);begin.invoke(null,device);set(flow,"activeSource","等待应答任务");set(flow,"preparationStartedAt",System.currentTimeMillis()-60000L);set(flow,"autoZeroWaiting",true);MachinePanelWorkflow.refresh(app);
            check("long auto-zero wait is explained instead of appearing idle",source.getToolTipText().contains("等待设备应答"));
            check("waiting alone never resubmits or discards a task",MachinePanelWorkflow.sourceLocked(app)&&Boolean.FALSE.equals(sender.getClass().getMethod("i").invoke(sender)));
            set(flow,"autoZeroWaiting",false);set(flow,"preparationStartedAt",System.currentTimeMillis());set(sender,"d",true);set(sender,"t",System.currentTimeMillis()-60000L);((java.util.List)get(sender,"j")).add(new Object());MachinePanelWorkflow.refresh(app);
            awaitUi(()->source.getToolTipText().contains("等待设备应答"));
            check("long command acknowledgement wait is explained",source.getToolTipText().contains("等待设备应答"));
            check("long commands are not automatically interrupted or replayed",Boolean.TRUE.equals(sender.getClass().getMethod("i").invoke(sender))&&MachinePanelWorkflow.sourceLocked(app));
            set(sender,"h",true);MachinePanelWorkflow.refresh(app);
            check("real paused sender shows paused source even after a long wait",source.getToolTipText().startsWith("已暂停：等待应答任务"));
            check("paused sender retains task and resume action",MachinePanelWorkflow.sourceLocked(app)&&((JButton)get(flow,"pause")).getText().contains("恢复"));
            check("paused estimate explains it is not physical completion",status.getToolTipText()!=null&&status.getToolTipText().contains("不代表机器已完成"));
            set(sender,"h",false);
            sender.getClass().getMethod("c").invoke(sender);
            Object machine=Class.forName("com.kenjoy.kenjoycnc.o.c").getField("e").get(null),priorType=get(machine,"type"),katType=null;
            for(Object type:priorType.getClass().getEnumConstants())if(((Enum)type).name().equals("K_AT"))katType=type;
            set(machine,"type",katType);Object queue=Class.forName("com.kenjoy.kenjoycnc.machine.kat.c").getMethod("a").invoke(null);set(queue,"b",true);
            sender.getClass().getMethod("addEventListener",String.class,Class.forName("flash.events.IEventListener")).invoke(sender,"Complete",queue);
            set(flow,"writeFailureLatch",true);set(flow,"handoffFailureMessage","队列任务中断");responder=dialogs(messages,JOptionPane.YES_OPTION);
            try{MachinePanelWorkflow.refresh(app);write.doClick(0);awaitUi(()->!flag(flow,"recovering"));check("queue-machine recovery also cancels its separate pending queue",Boolean.FALSE.equals(queue.getClass().getMethod("c").invoke(queue))&&!MachinePanelWorkflow.sourceLocked(app));}
            finally{responder.stop();set(machine,"type",priorType);}
            set(firmware,"d",priorConnected);set(flow,"autoZeroWaiting",false);set(flow,"activeSource",null);clear.invoke(null,device);MachinePanelWorkflow.refresh(app);
            check("ordinary write label returns after recovery",write.getText().equals("书写"));
        }catch(Exception e){throw new RuntimeException(e);}});
        runNoteResumeRetentionCheck(app);
    }
    private static void runNoteResumeRetentionCheck(Object app)throws Exception{
        if(System.getProperty("note.fonts")==null)return;
        final Object[] holders=new Object[2];
        EventQueue.invokeAndWait(()->{try{
            JComboBox modes=(JComboBox)get(app,"e");
            Object noteMode=modes.getItemAt(0);
            if(noteMode!=null&&noteMode.getClass().getName().endsWith(".NoteTab")){
                holders[0]=noteMode;
                holders[1]=noteMode.getClass().getMethod("document").invoke(noteMode);
            }
        }catch(Exception e){throw new RuntimeException(e);}});
        if(holders[0]==null)return;
        Object noteMode=holders[0],priorDoc=holders[1];
        try{
            ResponsiveChecks.sample(noteMode);
            EventQueue.invokeAndWait(()->{try{
                JFrame frame=(JFrame)app.getClass().getMethod("f").invoke(app);
                Object flow=frame.getRootPane().getClientProperty("modernization.machinePanel"),device=UiStyle.field(app,"y");
                JButton write=(JButton)get(device,"m");
                Object doc=noteMode.getClass().getMethod("document").invoke(noteMode);
                java.util.List<?> atoms=(java.util.List<?>)get(doc,"atoms");
                long firstId=((Number)get(atoms.get(0),"id")).longValue();
                noteMode.getClass().getMethod("setResumeGlyphId",long.class).invoke(noteMode,firstId);
                set(flow,"activeSource","笔记当前页 1/1 · 续写");set(flow,"writeFailureLatch",true);set(flow,"handoffFailureMessage","USB 连接已断开");
                java.util.List<String> messages=new ArrayList<String>();
                javax.swing.Timer responder=dialogs(messages,JOptionPane.YES_OPTION);
                try{MachinePanelWorkflow.refresh(app);write.doClick(0);
                    awaitUi(()->!flag(flow,"recovering"));
                    check("clearing interrupted lock retains note resume selection",
                        Boolean.TRUE.equals(noteMode.getClass().getMethod("hasResumeProgress").invoke(noteMode))
                        &&Long.valueOf(firstId).equals(noteMode.getClass().getMethod("resumeGlyphIdForPage",int.class).invoke(noteMode,0)));
                }finally{responder.stop();noteMode.getClass().getMethod("clearResumeProgress").invoke(noteMode);set(flow,"writeFailureLatch",false);set(flow,"activeSource",null);set(flow,"handoffFailureMessage",null);MachinePanelWorkflow.refresh(app);}
            }catch(Exception e){throw new RuntimeException(e);}});
        }finally{
            Class.forName("com.kenjoy.kenjoycnc.note.NoteWriteProgress").getMethod("clear").invoke(null);
            EventQueue.invokeAndWait(()->{try{noteMode.getClass().getMethod("setDocument",priorDoc.getClass()).invoke(noteMode,priorDoc);}catch(Exception e){throw new RuntimeException(e);}});
            ResponsiveChecks.waitNote(noteMode);
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==1&&"--pause-status".equals(args[0])){runPauseStatusChecks();System.out.println("MACHINE_PAUSE_STATUS_COMPLETE");return;}
        ProductionChecks.out=Paths.get(System.getProperty("trial.evidence"));Files.createDirectories(ProductionChecks.out);
        try{Class.forName("NoteLauncher").getMethod("main",String[].class).invoke(null,(Object)args);Object app=Class.forName("com.kenjoy.kenjoycnc.KenjoyDraw").getMethod("a").invoke(null);run(app);System.out.println("MACHINE_WRITE_COMPLETE");System.exit(0);}catch(Throwable error){error.printStackTrace();System.exit(2);}
    }
}

import com.kenjoy.kenjoycnc.data.*;
import com.kenjoy.kenjoycnc.note.*;
import com.kenjoy.kenjoycnc.processTabs.*;
import java.awt.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.*;
import javax.swing.*;

/** Focused actual NoteTab checks. Each scenario runs in its own JVM/home without a main window or device. */
public final class NoteStateChecks {
    interface Work<T>{T run()throws Exception;}
    static int checks,failures;static NoteTab tab;static g canvas;static JScrollPane scroll;
    static <T>T edt(Work<T> work)throws Exception{Object[] result={null};Throwable[] error={null};SwingUtilities.invokeAndWait(()->{try{result[0]=work.run();}catch(Throwable e){error[0]=e;}});if(error[0] instanceof Exception)throw (Exception)error[0];if(error[0] instanceof Error)throw (Error)error[0];return (T)result[0];}
    static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static void check(String name,boolean passed){checks++;if(!passed)failures++;System.out.println((passed?"PASS ":"FAIL ")+name);}
    static void waitDone()throws Exception{long deadline=System.currentTimeMillis()+30000;while(edt(()->tab.isBusy())){if(System.currentTimeMillis()>deadline)throw new AssertionError("layout timeout");Thread.sleep(25);}}
    static void stop()throws Exception{edt(()->{((Timer)field(tab,"layoutTimer")).stop();((Timer)field(tab,"recoveryTimer")).stop();return null;});}
    static void create()throws Exception{edt(()->{tab=new NoteTab(null);canvas=(g)tab.createCanvas();scroll=new JScrollPane(canvas);scroll.setSize(640,400);scroll.doLayout();canvas.setSize(canvas.getPreferredSize());scroll.getViewport().setViewSize(canvas.getPreferredSize());return null;});}
    static NoteDocument document(String text){NoteDocument d=new NoteDocument();d.page=new PageInfo("probe",210,297,20,20,20,20,30,95,true);d.page.fontPath=new String[0];d.replace(0,0,text);return d;}
    static void view()throws Exception{
        NoteRecovery.Snapshot seed=new NoteRecovery.Snapshot();StringBuilder body=new StringBuilder();for(int i=0;i<100;i++)body.append("😀\n");seed.document=document(body.toString());seed.editing=true;seed.processingAngle=90;seed.scrollY=1000;seed.savedState=seed.document.encode();NoteRecovery.save(seed);
        create();long end=System.currentTimeMillis()+30000;while(!edt(()->tab.document().text.equals(seed.document.text))){if(System.currentTimeMillis()>end)throw new AssertionError("restore timeout");Thread.sleep(25);}waitDone();edt(()->null);
        check("restoration uses saved viewport origin",edt(()->scroll.getViewport().getViewPosition().y==1000));
        check("restoration retains processing rotation",edt(()->canvas.processingAngle()==90));
        edt(()->{canvas.setDisplayScale(1.4f);scroll.getViewport().setViewSize(canvas.getPreferredSize());scroll.getViewport().setViewPosition(new Point(20,1000));tab.onDeactive();canvas=(g)tab.createCanvas();scroll.setViewportView(canvas);scroll.doLayout();scroll.getViewport().setViewSize(canvas.getPreferredSize());return null;});edt(()->null);
        check("canvas recreation retains angle and scale",edt(()->canvas.processingAngle()==90&&Math.abs(canvas.displayScale()-1.4f)<.0001f));
        check("canvas recreation retains viewport origin",edt(()->scroll.getViewport().getViewPosition().equals(new Point(20,1000))));
        String unchanged=edt(()->tab.document().encode());int history=edt(()->tab.session().undoSize());
        edt(()->{tab.setInlineEditing(false);((JFormattedTextField)field(tab,"tfWidth")).setValue(-5);((JToggleButton)field(tab,"editToggle")).doClick();return null;});
        check("invalid edit-mode page never enters document or history",edt(()->tab.document().encode().equals(unchanged)&&tab.session().undoSize()==history));
        check("failed edit-mode toggle matches actual editor state",edt(()->!tab.isInlineEditing()&&!((JToggleButton)field(tab,"editToggle")).isSelected()));
        check("invalid unapplied page cannot be saved",edt(()->{try{tab.saveDocument(Paths.get(System.getProperty("user.home"),"invalid.kjnote"));return false;}catch(java.io.IOException expected){return true;}}));
        edt(()->{((JFormattedTextField)field(tab,"tfWidth")).setValue(210);tab.setInlineEditing(true);canvas.textEditor().setCaretPosition(0);((JButton)field(tab,"btnNextPage")).doClick();return null;});
        check("next-page navigation is independent of unchanged caret",edt(()->tab.getCurrentPageIndex()==1));
        edt(()->{tab.selectionChanged();return null;});check("control refresh does not reverse explicit page navigation",edt(()->tab.getCurrentPageIndex()==1));
        edt(()->{canvas.textEditor().setCaretPosition(2);return null;});check("moving caret updates current page",edt(()->tab.getCurrentPageIndex()==0));
        edt(()->{JFormattedTextField page=(JFormattedTextField)field(tab,"tfCurPage");page.setValue(3);page.postActionEvent();return null;});
        check("typed page navigation also scrolls to requested page",edt(()->tab.getCurrentPageIndex()==2&&scroll.getViewport().getViewPosition().y>1000));
        edt(()->{canvas.textEditor().setCaretPosition(0);tab.session().replace(0,0,"😀",NoteEditSession.Kind.TYPE);tab.changed();canvas.syncEditor();((JButton)field(tab,"btnNextPage")).doClick();return null;});
        waitDone();check("layout completion preserves explicit page navigation",edt(()->tab.getCurrentPageIndex()==1));stop();
    }
    static void closeRace()throws Exception{
        NoteRecovery.Snapshot seed=new NoteRecovery.Snapshot();seed.document=document("recovery must survive");seed.dirty=true;NoteRecovery.save(seed);
        CountDownLatch started=new CountDownLatch(10),release=new CountDownLatch(1);
        for(int i=0;i<10;i++)new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{started.countDown();release.await();return null;}}.execute();
        if(!started.await(10,TimeUnit.SECONDS))throw new AssertionError("workers unavailable");
        try{edt(()->{tab=new NoteTab(null);return null;});edt(()->null);check("close can finish while untouched initial recovery waits",edt(()->tab.prepareEditorClose()));NoteRecovery.Snapshot stored=NoteRecovery.load();check("close before initial restore preserves existing recovery",stored.document.text.equals(seed.document.text)&&stored.dirty);}
        finally{release.countDown();}stop();
    }
    static void legacy()throws Exception{
        NoteRecovery.clear();create();edt(()->null);Thread.sleep(80);
        NoteFonts fonts=new NoteFonts(new java.io.File(System.getProperty("note.fonts")));edt(()->{Field f=tab.getClass().getDeclaredField("catalogue");f.setAccessible(true);f.set(tab,fonts);return null;});NoteFonts.Entry chosen=null;for(NoteFonts.Entry entry:fonts.entries())if(entry.file.getName().equals("晓李手写_kvenjoy_aren.gfont")){chosen=entry;break;}if(chosen==null)throw new AssertionError("fixture font unavailable");
        NoteDocument d=document("春");d.pool.add(fonts.ref(chosen));d.page.fontPath=new String[]{chosen.file.getName()};d.page.fontChance=new int[]{1};
        edt(()->{tab.setDocument(d);return null;});waitDone();check("fixture starts with available glyph",edt(()->tab.isOutputReady()));
        edt(()->{tab.session().setInputFonts(new String[0],new int[0]);boolean accepted=tab.onNoteContentSet(new StringBuffer("春春"),tab.document().format);check("legacy content edit commits",accepted);return null;});waitDone();
        check("legacy new atoms freeze current empty font strategy",edt(()->{NoteDocument.Atom a=tab.document().atoms.get(0);return a.inputFonts!=null&&a.inputFonts.length==0;}));
        check("legacy new glyphs honor current strategy rather than old pool",edt(()->!tab.isOutputReady()&&!tab.noteLayout().missing.isEmpty()));stop();
    }
    public static void run()throws Exception{
        if(!Boolean.getBoolean("note.state.isolated"))throw new IllegalStateException("requires isolated test JVM");
        java.security.Provider windows=java.security.Security.getProvider("SunMSCAPI");if(windows!=null){java.security.Security.removeProvider("SunMSCAPI");java.security.Security.insertProviderAt(windows,1);}
        MachineData machine=new MachineData();machine.name="NoteStateChecks";machine.type=com.kenjoy.kenjoycnc.machine.MachineType.Grbl;com.kenjoy.kenjoycnc.o.c.e=machine;com.kenjoy.kenjoycnc.o.c.c="kvenjoy";
        String scenario=System.getProperty("note.state.scenario","view");if("view".equals(scenario))view();else if("close".equals(scenario))closeRace();else if("legacy".equals(scenario))legacy();else throw new IllegalArgumentException(scenario);
        System.out.println("NOTE_STATE_CHECKS "+scenario+" checks="+checks+" failures="+failures);if(failures!=0)throw new AssertionError("state regressions="+failures);
    }
    /** Called by the real application's inline suite; child homes keep restore fixtures away from its active draft. */
    public static void runForked()throws Exception{
        Path evidence=Paths.get(System.getProperty("note.evidence"));Files.createDirectories(evidence);
        for(String scenario:new String[]{"view","close","legacy"}){
            Path work=Files.createTempDirectory(evidence,"note-state-"+scenario+"-");Path log=work.resolve("result.log"),temporary=Files.createDirectory(work.resolve("tmp"));
            String executable=Paths.get(System.getProperty("java.home"),"bin","java.exe").toString();
            ProcessBuilder builder=new ProcessBuilder(executable,"-Xverify:all","-Dfile.encoding=UTF-8","-Dkvenjoy.processingDpi=96","-Dkvenjoy.connection.disabled=true","-Dnote.state.isolated=true","-Dnote.state.scenario="+scenario,"-Duser.home="+work.resolve("home"),"-Djava.io.tmpdir="+temporary,"-Djava.library.path="+System.getProperty("java.library.path"),"-Djna.library.path="+System.getProperty("jna.library.path",""),"-Dnote.fonts="+System.getProperty("note.fonts"),"-cp",System.getProperty("java.class.path"),"NoteStateChecks").directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
            builder.environment().put("TEMP",temporary.toString());builder.environment().put("TMP",temporary.toString());Process process=builder.start();
            if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("state check timed out: "+scenario);}
            if(process.exitValue()!=0)throw new AssertionError("state check failed: "+scenario+"; "+log);
            System.out.print(new String(Files.readAllBytes(log),java.nio.charset.StandardCharsets.UTF_8));
        }
        Files.write(evidence.resolve("NOTE-STATE-COMPLETE.txt"),"view, close and legacy state checks passed".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static void main(String[] args){try{run();System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}

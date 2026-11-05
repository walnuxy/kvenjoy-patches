import com.kenjoy.kenjoycnc.KenjoyDraw;
import com.kenjoy.kenjoycnc.note.*;
import com.kenjoy.kenjoycnc.processTabs.NoteTab;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** Runs last in the isolated application checks; never sends to a device or exits the JVM. */
public final class InlineWorkflowChecks {
    interface Checked<T>{T run()throws Exception;}
    static NoteTab tab;static JTextArea editor;static Path out;
    static final List<String> rows=new ArrayList<String>();
    static <T>T edt(Checked<T> action)throws Exception{
        if(SwingUtilities.isEventDispatchThread())return action.run();
        final Object[] result={null};final Throwable[] failure={null};
        SwingUtilities.invokeAndWait(()->{try{result[0]=action.run();}catch(Throwable e){failure[0]=e;}});
        if(failure[0] instanceof Exception)throw (Exception)failure[0];
        if(failure[0] instanceof Error)throw (Error)failure[0];
        return (T)result[0];
    }
    static Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{
        try{return target.getClass().getMethod(name,types).invoke(target,args);}
        catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;if(cause instanceof Error)throw (Error)cause;throw error;}
    }
    static Object call(Object target,String name)throws Exception{return call(target,name,new Class<?>[0]);}
    static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static void check(String name,boolean pass)throws Exception{
        rows.add((pass?"PASS":"FAIL")+"\t"+name);Files.write(out.resolve("inline-workflow-checks.tsv"),rows,StandardCharsets.UTF_8);
        if(!pass)throw new AssertionError(name);System.out.println("INLINE_WORKFLOW PASS "+name);
    }
    static void waitDone()throws Exception{long end=System.currentTimeMillis()+60000;while(edt(()->tab.isBusy())){if(System.currentTimeMillis()>end)throw new AssertionError("workflow layout timeout");Thread.sleep(40);}}
    static boolean ready()throws Exception{return edt(()->Boolean.TRUE.equals(call(tab,"isOutputReady")));}
    static String identity()throws Exception{return edt(()->String.valueOf(call(tab,"editSourceIdentity")));}
    static void append(String text)throws Exception{edt(()->{editor.setCaretPosition(editor.getDocument().getLength());editor.replaceSelection(text);return null;});}
    public static void run()throws Exception{
        if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Run checks on the check thread");
        out=Paths.get(System.getProperty("note.evidence")).toAbsolutePath();Files.createDirectories(out);
        // This class writes recovery preferences. The launcher must use a disposable home.
        String profile=System.getenv("USERPROFILE");
        if(profile!=null&&Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize().equals(Paths.get(profile).toAbsolutePath().normalize()))throw new IllegalStateException("Workflow checks require an isolated user.home");
        edt(()->{JComboBox modes=(JComboBox)field(KenjoyDraw.a(),"e");for(int i=0;i<modes.getItemCount();i++)if(modes.getItemAt(i) instanceof NoteTab){modes.setSelectedIndex(i);tab=(NoteTab)modes.getItemAt(i);break;}editor=(JTextArea)call(field(tab,"notePanel"),"textEditor");return null;});
        NoteDocument sample=NoteChecks.sample(tab.catalogue());
        edt(()->{tab.setDocument(sample);return null;});waitDone();
        edt(()->{call(tab,"setInlineEditing",new Class<?>[]{boolean.class},true);editor.setCaretPosition(editor.getDocument().getLength());return null;});
        check("new valid article is ready for output",ready());
        String frozenIdentity=identity();
        Object generator=edt(()->{if(!tab.beginMachineJob())throw new AssertionError("capture machine snapshot");return tab.getGcodeGenerator();});
        NoteExportHooks.begin(2);
        try{
            NoteExportHooks.Job job=NoteExportHooks.current(tab);String captured=job.document.encode();NoteLayout capturedLayout=job.layout;
            append("😀");waitDone();
            check("new unresolved edit blocks new output",!ready()&&edt(()->tab.document().text.endsWith("😀")&&!tab.noteLayout().missing.isEmpty()));
            check("task version differs from next editable draft",!frozenIdentity.equals(identity()));
            check("active generator remains the captured object",edt(()->tab.getGcodeGenerator()==generator));
            check("active export retains captured document and layout",job.document.encode().equals(captured)&&job.layout==capturedLayout);
            check("active export can render its valid captured page",tab.renderToImage(0)!=null);
        }finally{NoteExportHooks.end();edt(()->{tab.endMachineJob();return null;});}
        check("new machine snapshot rejects unresolved draft",edt(()->!tab.beginMachineJob()));
        boolean refused=false;try{NoteExportHooks.begin(1);}catch(IllegalStateException expected){refused=true;}finally{NoteExportHooks.end();}
        check("new export rejects unresolved draft",refused);
        boolean imageRefused=edt(()->{try{tab.renderToImage(0);return false;}catch(IllegalStateException expected){return true;}});
        check("new image output rejects unresolved draft",imageRefused);
        Path article=out.resolve("workflow-unresolved.kjnote");
        edt(()->{tab.saveDocument(article);return null;});
        check("unresolved article still saves and reopens",NoteDocument.load(article).text.endsWith("😀"));
        edt(()->{call(tab,"history",new Class<?>[]{boolean.class},false);return null;});waitDone();
        check("undo repairs output readiness",ready());
        check("undo to captured semantics restores task identity",frozenIdentity.equals(identity()));

        // Inject a missing next-copy candidate without changing the live article.
        edt(()->{tab.setRandomCopies(true);return null;});NoteExportHooks.begin(2);
        try{
            NoteExportHooks.Job job=NoteExportHooks.current(tab);job.document.replace(0,job.document.text.length(),"😀");
            NoteDocument previous=job.document;NoteLayout previousLayout=job.layout;Object previousPaths=field(job,"paths");Object previousCount=field(job,"completed");
            boolean aborted=false;try{NoteExportHooks.afterCopy(tab);}catch(NoteExportHooks.BatchAbort expected){aborted=true;}
            check("random next copy aborts on missing text",aborted);
            check("failed random copy leaves prior snapshot and progress intact",job.document==previous&&job.layout==previousLayout&&field(job,"paths")==previousPaths&&field(job,"completed").equals(previousCount));
        }finally{NoteExportHooks.end();edt(()->{tab.setRandomCopies(false);return null;});}

        // Ensure close serializes after an older asynchronous recovery and includes the latest confirmed keystroke.
        NoteRecovery.setClosePolicy(NoteRecovery.ClosePolicy.AUTO_RECOVER);
        edt(()->{tab.saveDocument(article);Method save=tab.getClass().getDeclaredMethod("saveRecoveryAsync");save.setAccessible(true);save.invoke(tab);return null;});
        final String[] latest={null};final int[] caret={0};final boolean[] closed={false};
        edt(()->{editor.setCaretPosition(editor.getDocument().getLength());editor.replaceSelection("春");latest[0]=tab.document().text;caret[0]=editor.getCaretPosition();closed[0]=Boolean.TRUE.equals(call(tab,"prepareEditorClose"));return null;});
        check("close flushes recovery without waiting for glyph layout",closed[0]);
        NoteRecovery.Snapshot recovered=NoteRecovery.load();
        check("close recovery contains latest confirmed text and caret",recovered!=null&&recovered.document.text.equals(latest[0])&&recovered.dot==caret[0]&&recovered.anchor==caret[0]);
        check("close recovery preserves dirty state and file association",recovered.dirty&&recovered.editing&&article.toString().equals(recovered.file)&&recovered.savedState!=null);
        check("automatic close leaves actively saved article unchanged",!NoteDocument.load(article).text.equals(latest[0]));
        Thread.sleep(200);edt(()->null);
        check("older recovery completion cannot overwrite close snapshot",NoteRecovery.load().document.text.equals(latest[0]));
        Files.write(out.resolve("INLINE_WORKFLOW_COMPLETE.txt"),(rows.size()+" checks passed\n").getBytes(StandardCharsets.UTF_8));
    }
}

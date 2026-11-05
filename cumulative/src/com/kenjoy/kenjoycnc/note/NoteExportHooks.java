package com.kenjoy.kenjoycnc.note;

import com.kenjoy.kenjoycnc.KenjoyDraw;
import com.kenjoy.kenjoycnc.data.*;
import com.kenjoy.kenjoycnc.g.e;
import com.kenjoy.kenjoycnc.model.Settings;
import com.kenjoy.kenjoycnc.processTabs.*;
import java.util.*;
import java.awt.geom.AffineTransform;
import javax.swing.SwingUtilities;

/** Called only from the batch-exporter's run method; no change to other panels. */
public final class NoteExportHooks {
    private static final ThreadLocal<Job> JOB=new ThreadLocal<Job>();
    public static final class Job {
        public final NoteTab owner;
        public NoteDocument document;
        public NoteLayout layout;
        public final int pageIndex;
        private final int copies;
        private int completed;
        private final boolean random;
        public final float dpi;
        private final float near;
        private final AffineTransform machine;
        private final int pageCount;
        private final NoteFonts fonts;
        private List<List<e>> paths;
        Job(NoteTab tab,int copies)throws Exception {
            if(!tab.isOutputReady())throw new IllegalStateException(tab.outputReadinessMessage());
            owner=tab;this.copies=copies;pageIndex=tab.getCurrentPageIndex();random=tab.randomCopies();document=tab.document();layout=tab.noteLayout();fonts=tab.catalogue();dpi=Settings.getInst().dpi;near=Settings.getInst().nearDst;machine=tab.machineTransform();pageCount=layout.pages.size();capturePaths();
        }
        private List<List<e>> paths(NoteLayout source){List<List<e>> all=new ArrayList<List<e>>();for(NoteLayout.Page p:source.pages){List<e> converted=new ArrayList<e>();for(e path:p.paths){float[] xy=path.a();byte[] flags=path.b();machine.transform(xy,0,xy,0,xy.length/2);e copy=new e();for(int i=0;i<flags.length;i++)if(flags[i]==0)copy.a(xy[2*i],xy[2*i+1]);else copy.b(xy[2*i],xy[2*i+1]);converted.add(copy);}all.add(converted);}return all;}
        private void capturePaths(){paths=paths(layout);}
        public com.kenjoy.kenjoycnc.c.a generator(int page){return paths.isEmpty()?null:new com.kenjoy.kenjoycnc.c.c(com.kenjoy.kenjoycnc.L.b(paths.get(page)),near,false);}
        public List generators(){List list=new ArrayList();for(int i=0;i<paths.size();i++)list.add(generator(i));return list;}
        void advance()throws Exception {
            int nextCompleted=completed+1;
            if(nextCompleted<copies&&random){
                NoteDocument candidate=document.copy();candidate.rerandomize();
                NoteLayout next=new NoteLayoutEngine(fonts,dpi).build(candidate);
                if(!next.missing.isEmpty())throw new IllegalStateException("随机副本存在缺字，导出已停止。请修复缺字后再导出。");
                if(next.pages.size()!=pageCount)throw new IllegalStateException("随机副本页数变化，导出已停止。请减小随机范围或关闭每份随机。");
                List<List<e>> converted=paths(next);
                document=candidate;layout=next;paths=converted;
            }
            completed=nextCompleted;
        }
    }
    public static void begin(final int copies) {
        final Job[] job={null};final RuntimeException[] failure={null};Runnable capture=()->{try{IProcessTab selected=KenjoyDraw.a().d();if(selected instanceof NoteTab)job[0]=new Job((NoteTab)selected,copies);}catch(Exception e){failure[0]=new IllegalStateException(e);}};
        try{if(SwingUtilities.isEventDispatchThread())capture.run();else SwingUtilities.invokeAndWait(capture);if(failure[0]!=null)throw failure[0];JOB.set(job[0]);}catch(Exception e){throw new IllegalStateException("无法冻结文章导出结果",e);}
    }
    public static Job current(NoteTab tab){Job job=JOB.get();return job!=null&&job.owner==tab?job:null;}
    public static final class BatchAbort extends Error {public BatchAbort(Throwable cause){super(cause.getMessage(),cause);}}
    public static void afterCopy(IProcessTab tab)throws Exception {Job job=JOB.get();if(job!=null&&job.owner==tab){try{job.advance();}catch(Exception e){throw new BatchAbort(e);}}else tab.process();}
    public static void failed(Object exporter,Throwable error){Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();final String message="导出失败，已停止："+String.valueOf(cause.getMessage());try{java.lang.reflect.Field running=exporter.getClass().getDeclaredField("y");running.setAccessible(true);running.setBoolean(exporter,false);java.lang.reflect.Method status=exporter.getClass().getDeclaredMethod("a",String.class);status.setAccessible(true);status.invoke(exporter,message);}catch(Exception cleanup){cleanup.printStackTrace();}System.err.println("NOTE_EXPORT_ERROR "+message);}
    public static IProcessTab active(KenjoyDraw app){Job job=JOB.get();return job==null?app.d():job.owner;}
    public static void end(){JOB.remove();}
}

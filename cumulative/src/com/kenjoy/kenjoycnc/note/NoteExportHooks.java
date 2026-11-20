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
        private final NoteDocument baseDocument;
        private final boolean effectRandom;
        private List<List<e>> paths;
        Job(NoteTab tab,int copies)throws Exception {
            if(!tab.isOutputReady())throw new IllegalStateException(tab.outputReadinessMessage());
            if(copies<1)throw new IllegalArgumentException("导出份数必须至少为 1。");
            owner=tab;this.copies=copies;pageIndex=tab.getCurrentPageIndex();random=tab.randomCopies();document=tab.document();layout=tab.noteLayout();fonts=tab.catalogue();dpi=Settings.getInst().dpi;near=Settings.getInst().nearDst;machine=tab.machineTransform();
            validateMachineTransform();
            if(layout==null)throw new IllegalStateException("文章布局尚未生成，无法冻结导出结果。");
            pageCount=layout.pages.size();if(pageCount==0||pageIndex<0||pageIndex>=pageCount)throw new IllegalStateException("当前文章页面不存在，无法冻结导出结果。");
            validateLayout(layout);capturePaths();validateMachinePaths(document,paths);
            baseDocument=document.copy();boolean enabled=false;for(NoteDocument.Atom a:baseDocument.atoms)enabled|=NoteAppearance.effective(baseDocument,a).randomMode!=0;effectRandom=enabled;
        }
        private void validateMachineTransform(){
            if(machine==null)throw new IllegalStateException("文章到机器坐标的变换无效。");
            double[] matrix=new double[6];machine.getMatrix(matrix);for(double value:matrix)if(Double.isNaN(value)||Double.isInfinite(value))throw new IllegalStateException("文章到机器坐标的变换包含无效数值。");
            double determinant=machine.getDeterminant();if(Double.isNaN(determinant)||Double.isInfinite(determinant)||determinant==0d)throw new IllegalStateException("文章到机器坐标的变换不可逆。");
        }
        private List<List<e>> paths(NoteLayout source){
            if(source==null||source.pages==null)throw new IllegalStateException("文章布局缺少页面，无法转换机器路径。");
            List<List<e>> all=new ArrayList<List<e>>();
            for(NoteLayout.Page page:source.pages){
                if(page==null||page.paths==null)throw new IllegalStateException("文章页面缺少笔迹路径，无法转换机器路径。");
                List<e> converted=new ArrayList<e>();
                for(e path:page.paths){
                    if(path==null)throw new IllegalStateException("文章笔迹包含空路径，已阻止输出。");
                    float[] sourceXY=path.a();byte[] flags=path.b();
                    if(sourceXY==null||flags==null||sourceXY.length!=flags.length*2)throw new IllegalStateException("文章笔迹坐标与绘制命令长度不匹配，已阻止输出。");
                    for(int i=0;i<flags.length;i++){
                        if(flags[i]!=0&&flags[i]!=1)throw new IllegalStateException("文章笔迹包含未知绘制命令，已阻止输出。");
                        if(!Float.isFinite(sourceXY[i*2])||!Float.isFinite(sourceXY[i*2+1]))throw new IllegalStateException("文章笔迹包含非有限坐标，已阻止输出。");
                    }
                    float[] xy=sourceXY.clone();machine.transform(sourceXY,0,xy,0,flags.length);e copy=new e();
                    for(int i=0;i<flags.length;i++){
                        float x=xy[i*2],y=xy[i*2+1];if(!Float.isFinite(x)||!Float.isFinite(y))throw new IllegalStateException("机器路径转换后包含非有限坐标，已阻止输出。");
                        if(flags[i]==0)copy.a(x,y);else copy.b(x,y);
                    }
                    converted.add(copy);
                }
                all.add(converted);
            }
            return all;
        }
        private void capturePaths(){paths=paths(layout);}
        public com.kenjoy.kenjoycnc.c.a generator(int page){return paths.isEmpty()?null:new com.kenjoy.kenjoycnc.c.c(com.kenjoy.kenjoycnc.L.b(paths.get(page)),near,false);}
        public List generators(){List list=new ArrayList();for(int i=0;i<paths.size();i++)list.add(generator(i));return list;}
        private NoteDocument copy(NoteDocument source,int index){
            if(effectRandom)return NoteAppearance.forCopy(baseDocument,index);
            NoteDocument candidate=source.copy();candidate.rerandomize();return candidate;
        }
        private NoteDocument copy(int index){return copy(document,index);}
        private void validateLayout(NoteLayout candidate){
            if(candidate==null)throw new IllegalStateException("随机副本未生成有效布局。");
            if(!candidate.missing.isEmpty())throw new IllegalStateException("随机副本存在缺字，请修复后再导出。");
            if(candidate.pages.size()!=pageCount)throw new IllegalStateException("随机副本页数变化，请减小随机范围或关闭每份随机。");
        }
        private void validateMachinePaths(NoteDocument candidate,List<List<e>> converted){
            if(converted==null||converted.size()!=pageCount)throw new IllegalStateException("机器路径页数与冻结页面数不一致，已阻止输出。");
            for(int page=0;page<pageCount;page++)NoteForbiddenZoneGeometry.validatePagePaths(candidate,page,dpi,machine,converted.get(page));
        }
        private void preflight()throws Exception{
            // Called on the exporter thread after the EDT snapshot, before any file is written.
            if(random){NoteDocument source=document;for(int index=1;index<copies;index++){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException("已取消随机副本预检");
                NoteDocument candidate=copy(source,index);NoteLayout next=new NoteLayoutEngine(fonts,dpi).build(candidate);validateLayout(next);
                List<List<e>> converted=paths(next);validateMachinePaths(candidate,converted);if(!effectRandom)source=candidate;
            }}
        }
        void advance()throws Exception {
            int nextCompleted=completed+1;
            if(nextCompleted<copies&&random){
                NoteDocument candidate=copy(nextCompleted);
                NoteLayout next=new NoteLayoutEngine(fonts,dpi).build(candidate);
                validateLayout(next);
                List<List<e>> converted=paths(next);
                validateMachinePaths(candidate,converted);
                document=candidate;layout=next;paths=converted;
            }
            completed=nextCompleted;
        }
    }
    public static void begin(final int copies) {
        final Job[] job={null};final RuntimeException[] failure={null};Runnable capture=()->{try{IProcessTab selected=KenjoyDraw.a().d();if(selected instanceof NoteTab)job[0]=new Job((NoteTab)selected,copies);}catch(Exception e){failure[0]=new IllegalStateException(e);}};
        try{if(SwingUtilities.isEventDispatchThread())capture.run();else SwingUtilities.invokeAndWait(capture);if(failure[0]!=null)throw failure[0];if(job[0]!=null)job[0].preflight();JOB.set(job[0]);}catch(Exception e){throw new IllegalStateException("无法冻结或预检文章导出结果",e);}
    }
    public static Job current(NoteTab tab){Job job=JOB.get();return job!=null&&job.owner==tab?job:null;}
    public static final class BatchAbort extends Error {public BatchAbort(Throwable cause){super(cause.getMessage(),cause);}}
    public static void afterCopy(IProcessTab tab)throws Exception {Job job=JOB.get();if(job!=null&&job.owner==tab){try{job.advance();}catch(Exception e){throw new BatchAbort(e);}}else tab.process();}
    public static void failed(Object exporter,Throwable error){Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();final String message="导出失败，已停止："+String.valueOf(cause.getMessage());try{java.lang.reflect.Field running=exporter.getClass().getDeclaredField("y");running.setAccessible(true);running.setBoolean(exporter,false);java.lang.reflect.Method status=exporter.getClass().getDeclaredMethod("a",String.class);status.setAccessible(true);status.invoke(exporter,message);}catch(Exception cleanup){cleanup.printStackTrace();}System.err.println("NOTE_EXPORT_ERROR "+message);}
    public static IProcessTab active(KenjoyDraw app){Job job=JOB.get();return job==null?app.d():job.owner;}
    public static void end(){JOB.remove();}
}

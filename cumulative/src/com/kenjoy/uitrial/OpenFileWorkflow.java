package com.kenjoy.uitrial;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JButton;
import javax.swing.SwingWorker;

/** Routes the main File > Open action to the mode that actually supports the chosen file. */
public final class OpenFileWorkflow {
    private static final String INSTALLED="modernization.openFileWorkflow";
    private final Object app;
    private final JFrame frame;
    private final JMenuItem open;
    private final JMenuItem save;
    private final JComboBox modes;
    private final JButton update,pdfPage;
    private SwingWorker<Void,Void> opening;

    private OpenFileWorkflow(Object app) throws Exception {
        this.app=app;
        frame=(JFrame)app.getClass().getMethod("f").invoke(app);
        open=(JMenuItem)field(app,"n");
        save=(JMenuItem)field(app,"u");
        modes=(JComboBox)field(app,"e");
        update=(JButton)field(app,"i");
        pdfPage=(JButton)field(app,"k");
    }

    public static void install(Object app) throws Exception {
        JFrame frame=(JFrame)app.getClass().getMethod("f").invoke(app);
        if(frame.getRootPane().getClientProperty(INSTALLED)!=null)return;
        OpenFileWorkflow workflow=new OpenFileWorkflow(app);
        for(ActionListener listener:workflow.open.getActionListeners())if(listener==app)workflow.open.removeActionListener(listener);
        workflow.open.addActionListener(event->workflow.openFile());
        for(ActionListener listener:workflow.save.getActionListeners())if(listener==app)workflow.save.removeActionListener(listener);
        workflow.save.addActionListener(event->workflow.saveFile(event));
        frame.getRootPane().putClientProperty(INSTALLED,workflow);
        workflow.refresh();
    }

    public static void refresh(Object app) throws Exception {
        JFrame frame=(JFrame)app.getClass().getMethod("f").invoke(app);
        Object workflow=frame.getRootPane().getClientProperty(INSTALLED);
        if(workflow instanceof OpenFileWorkflow)((OpenFileWorkflow)workflow).refresh();
    }

    private void refresh() throws Exception {
        open.setEnabled(opening==null&&extensions().length>0);
        Object current=modes.getSelectedItem();
        update.setText("更新");
        update.setToolTipText(isNote(current)?"应用页面参数并重新随机未指定字体的字形":null);
        if(isNote(current)){pdfPage.setVisible(true);pdfPage.setEnabled(false);pdfPage.setToolTipText("笔记模式没有当前 PDF；请在图片或表格模式打开 PDF 后使用");}
        else{pdfPage.setToolTipText(null);File selected=(File)field(app,"h");pdfPage.setEnabled(selected!=null&&selected.getName().toLowerCase(Locale.ROOT).endsWith(".pdf"));}
    }

    private void saveFile(ActionEvent event){
        Object current=modes.getSelectedItem();
        if(!isNote(current)){
            if(app instanceof ActionListener)((ActionListener)app).actionPerformed(event);
            return;
        }
        try{current.getClass().getMethod("saveDocumentCurrent").invoke(current);}
        catch(Exception error){
            Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
            JOptionPane.showMessageDialog(frame,"保存文章失败："+cause.getMessage(),"保存文章",JOptionPane.ERROR_MESSAGE);
        }
    }

    private String[] extensions() throws Exception {
        LinkedHashSet<String> found=new LinkedHashSet<String>();
        for(int i=0;i<modes.getItemCount();i++){
            Object mode=modes.getItemAt(i);
            String[] supported=(String[])mode.getClass().getMethod("getSupportFile").invoke(mode);
            if(supported!=null)for(String extension:supported)if(extension!=null&&!extension.isEmpty())found.add(extension.toLowerCase(Locale.ROOT));
        }
        return found.toArray(new String[found.size()]);
    }

    private void openFile() {
        try {
            if(MachinePanelWorkflow.sourceLocked(app)){
                JOptionPane.showMessageDialog(frame,"正在准备或执行书写，请等待任务结束后再打开文件。","打开文件",JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String[] filters=extensions();
            if(filters.length==0)return;
            File file=(File)Class.forName("com.kenjoy.kenjoycnc.L")
                .getMethod("a",Component.class,String.class,String.class,String[].class)
                .invoke(null,frame,"选择文件","open_all_formats",filters);
            if(file==null)return;
            Object current=modes.getSelectedItem();
            List<Object> matches=new ArrayList<Object>();
            for(int i=0;i<modes.getItemCount();i++){
                Object mode=modes.getItemAt(i);
                if(Boolean.TRUE.equals(mode.getClass().getMethod("isSupport",String.class).invoke(mode,file.getName())))matches.add(mode);
            }
            if(matches.isEmpty()){JOptionPane.showMessageDialog(frame,"软件不支持该文件格式："+file.getName(),"打开文件",JOptionPane.WARNING_MESSAGE);return;}
            Object target=current!=null&&matches.contains(current)?current:matches.size()==1?matches.get(0):
                JOptionPane.showInputDialog(frame,"请选择处理此文件的模式：","打开文件",JOptionPane.QUESTION_MESSAGE,null,matches.toArray(),matches.get(0));
            if(target==null)return;
            if(target!=current&&isNote(current)){
                if(Boolean.TRUE.equals(current.getClass().getMethod("isBusy").invoke(current))){JOptionPane.showMessageDialog(frame,"请等待文章排版完成。","打开文件",JOptionPane.INFORMATION_MESSAGE);return;}
                if((Boolean.TRUE.equals(current.getClass().getMethod("isDirty").invoke(current))||Boolean.TRUE.equals(current.getClass().getMethod("hasUnappliedPageSettings").invoke(current)))&&
                    JOptionPane.showConfirmDialog(frame,"当前文章或版式参数尚未保存，仍要切换模式？","打开文件",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            }
            if(isNote(target)){
                Runnable switchAfterLoad=target==current?null:()->switchWhenUnlocked(target);
                Runnable showFailure=()->showOpenFailure(target);
                if(!Boolean.TRUE.equals(target.getClass().getMethod("openDocument",File.class,Runnable.class,Runnable.class).invoke(target,file,switchAfterLoad,showFailure))){
                    if(target.getClass().getMethod("lastFailure").invoke(target)!=null)showOpenFailure(target);
                    return;
                }
            }else{
                startProbedOpen(file,target,current);
            }
        }catch(Exception error){
            Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
            JOptionPane.showMessageDialog(frame,"打开文件失败："+cause.getMessage(),"打开文件",JOptionPane.ERROR_MESSAGE);
            cause.printStackTrace();
        }
    }

    private void startProbedOpen(File file,Object target,Object current){
        if(opening!=null)return;
        final javax.swing.JDialog progress=new javax.swing.JDialog(frame,"正在检查文件",false);
        final JButton cancel=new JButton("取消打开");final java.util.concurrent.atomic.AtomicBoolean stop=new java.util.concurrent.atomic.AtomicBoolean(),finished=new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicReference<Thread> reader=new java.util.concurrent.atomic.AtomicReference<Thread>();
        progress.setLayout(new java.awt.BorderLayout(8,8));progress.add(new javax.swing.JLabel("正在校验 "+file.getName()+"…"),java.awt.BorderLayout.CENTER);progress.add(cancel,java.awt.BorderLayout.SOUTH);progress.setSize(340,110);progress.setLocationRelativeTo(frame);
        opening=new SwingWorker<Void,Void>(){
            protected Void doInBackground()throws Exception{Thread currentThread=Thread.currentThread();synchronized(reader){reader.set(currentThread);}try{if(stop.get())return null;preflight(file);return null;}finally{synchronized(reader){reader.compareAndSet(currentThread,null);}}}
            protected void done(){
                finished.set(true);opening=null;progress.dispose();try{refresh();}catch(Exception error){showOpenError(error);}
                if(stop.get())return;
                try{get();if(modes.getSelectedItem()!=current||MachinePanelWorkflow.sourceLocked(app))throw new java.io.IOException("检查期间模式或书写状态已变化，请重新打开文件");commitProbedOpen(file,target,current);}
                catch(Exception error){showOpenError(error);}
            }
        };
        Runnable stopOpen=()->{if(finished.get()||stop.getAndSet(true))return;synchronized(reader){Thread thread=reader.get();if(thread!=null)thread.interrupt();}cancel.setEnabled(false);cancel.setText("正在停止校验…");};
        cancel.addActionListener(e->stopOpen.run());progress.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
        progress.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){stopOpen.run();}});
        open.setEnabled(false);opening.execute();progress.setVisible(true);
    }

    private void commitProbedOpen(File file,Object target,Object current)throws Exception{
        File previous=(File)field(app,"h");String previousTitle=frame.getTitle();boolean previousPdfEnabled=pdfPage.isEnabled();int priorWord=wordCount(target);
        Map<Field,Object> imageState=imageState(target);Object previousExcelImage=excelReference(target);Object previousPdfImage=null;float previousPdfScale=0;
        if(file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")){
            Class<?> pdf=Class.forName("com.kenjoy.kenjoycnc.z");Field preview=pdf.getField("a"),scale=pdf.getField("b");
            previousPdfImage=preview.get(null);previousPdfScale=scale.getFloat(null);preview.set(null,null);
        }
        try{
            if(target!=current){modes.setSelectedItem(target);if(modes.getSelectedItem()!=target)throw new java.io.IOException("无法切换到目标模式");}
            loadMainFile(file);
            verifyLoaded(target,file,priorWord,previousExcelImage);
            pdfPage.setEnabled(file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf"));
        }catch(Exception openFailure){
            try{trimWord(target,priorWord);}catch(Exception cleanupFailure){openFailure.addSuppressed(cleanupFailure);}
            try{
                if(current!=null&&modes.getSelectedItem()!=current)modes.setSelectedItem(current);
                if(modes.getSelectedItem()==current){
                    if(imageState==null&&!excelTarget(target))loadMainFile(previous);
                    else{
                        if(imageState!=null){for(Map.Entry<Field,Object> entry:imageState.entrySet())entry.getKey().set(target,entry.getValue());((Component)target).repaint();}
                        else restoreExcelReference(target,previousExcelImage);
                        Field fileField=app.getClass().getDeclaredField("h");fileField.setAccessible(true);fileField.set(app,previous);frame.setTitle(previousTitle);
                    }
                }
                else throw new java.io.IOException("原模式未能恢复");
            }catch(Exception recoveryFailure){
                openFailure.addSuppressed(recoveryFailure);
                Field fileField=app.getClass().getDeclaredField("h");fileField.setAccessible(true);fileField.set(app,previous);frame.setTitle(previousTitle);
            }
            pdfPage.setEnabled(previousPdfEnabled);
            if(file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf"))try{
                Class<?> pdf=Class.forName("com.kenjoy.kenjoycnc.z");pdf.getField("a").set(null,previousPdfImage);pdf.getField("b").setFloat(null,previousPdfScale);
            }catch(Exception cleanupFailure){openFailure.addSuppressed(cleanupFailure);}
            throw openFailure;
        }
    }

    private void showOpenError(Throwable error){Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();JOptionPane.showMessageDialog(frame,"打开文件失败："+cause.getMessage(),"打开文件",JOptionPane.ERROR_MESSAGE);cause.printStackTrace();}

    private void loadMainFile(File file)throws Exception{
        Method load=app.getClass().getDeclaredMethod("a",File.class);load.setAccessible(true);load.invoke(app,file);
    }
    private Map<Field,Object> imageState(Object target)throws Exception{
        Class<?> imageTab=Class.forName("com.kenjoy.kenjoycnc.processTabs.ImageTab");if(!imageTab.isInstance(target))return null;
        Map<Field,Object> state=new LinkedHashMap<Field,Object>();
        for(Class<?> type=target.getClass();type!=null&&type!=javax.swing.JPanel.class;type=type.getSuperclass())
            for(Field field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&!Modifier.isFinal(field.getModifiers())&&!field.getName().equals("animator")&&!field.getName().equals("canvas")){field.setAccessible(true);state.put(field,field.get(target));}
        return state;
    }
    private boolean excelTarget(Object target){return target.getClass().getName().equals("com.kenjoy.kenjoycnc.processTabs.ExcelTab");}
    private Object excelCanvas(Object target)throws Exception{if(!excelTarget(target))return null;Field field=target.getClass().getDeclaredField("canvas");field.setAccessible(true);return field.get(target);}
    private Object excelReference(Object target)throws Exception{Object canvas=excelCanvas(target);return canvas==null?null:canvas.getClass().getMethod("i").invoke(canvas);}
    private void restoreExcelReference(Object target,Object image)throws Exception{Object canvas=excelCanvas(target);if(canvas==null)return;Field reference=canvas.getClass().getDeclaredField("k");reference.setAccessible(true);reference.set(canvas,image);((Component)canvas).repaint();}
    private void verifyLoaded(Object target,File file,int priorWord,Object priorExcelImage)throws Exception{
        String type=target.getClass().getName(),name=file.getName().toLowerCase(Locale.ROOT);
        Class<?> imageTab=Class.forName("com.kenjoy.kenjoycnc.processTabs.ImageTab");
        if(imageTab.isInstance(target)){
            Field image=imageTab.getDeclaredField("image");image.setAccessible(true);
            if(image.get(target)==null)throw new java.io.IOException("图像未能加载");
        }
        if(name.endsWith(".pdf")){
            Field selected=Class.forName("com.kenjoy.kenjoycnc.z").getField("a");
            if(selected.get(null)==null)throw new java.io.IOException("未选择 PDF 页面");
        }
        if(type.equals("com.kenjoy.kenjoycnc.processTabs.ExcelTab")){
            Object canvas=app.getClass().getMethod("e").invoke(app);
            Object loaded=canvas==null?null:canvas.getClass().getMethod("i").invoke(canvas);
            if(loaded==null||loaded==priorExcelImage)throw new java.io.IOException(name.endsWith(".pdf")?"未确认 PDF 页面":"图片未能加载到表格");
        }
        if(priorWord>=0&&wordCount(target)<=priorWord)throw new java.io.IOException("WORD 文件未能导入");
    }
    private int wordCount(Object target)throws Exception{
        if(!target.getClass().getName().equals("com.kenjoy.kenjoycnc.processTabs.k"))return -1;
        Field controller=target.getClass().getDeclaredField("a");controller.setAccessible(true);Object editor=controller.get(target);
        Field paragraphs=editor.getClass().getDeclaredField("G");paragraphs.setAccessible(true);return ((List)paragraphs.get(editor)).size();
    }
    private void trimWord(Object target,int prior)throws Exception{
        if(prior<0)return;Field controller=target.getClass().getDeclaredField("a");controller.setAccessible(true);Object editor=controller.get(target);
        Field paragraphs=editor.getClass().getDeclaredField("G");paragraphs.setAccessible(true);List list=(List)paragraphs.get(editor);while(list.size()>prior)list.remove(list.size()-1);
    }
    static void preflight(File file)throws Exception{
        if(!Files.isRegularFile(file.toPath())||!Files.isReadable(file.toPath())||Files.size(file.toPath())==0)throw new java.io.IOException("文件不存在、为空或不可读取："+file.getName());
        String name=file.getName().toLowerCase(Locale.ROOT),extension=name.substring(name.lastIndexOf('.')+1);
        if(Arrays.asList("jpg","jpeg","png","bmp","gif").contains(extension)){
            try(ImageInputStream input=ImageIO.createImageInputStream(file)){
                if(input==null)throw new java.io.IOException("无法读取图像");
                java.util.Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext())throw new java.io.IOException("图像格式或内容无效");
                ImageReader reader=readers.next();try{reader.setInput(input);long width=reader.getWidth(0),height=reader.getHeight(0);if(width<=0||height<=0||width*height>4_000_000)throw new java.io.IOException("图像尺寸无效或过大（最多 400 万像素）");if(reader.read(0)==null)throw new java.io.IOException("图像内容无法解码");}finally{reader.dispose();}
            }
        }else if(extension.equals("pdf")){
            if(Files.size(file.toPath())>128L*1024*1024)throw new java.io.IOException("PDF 文件超过 128 MB 上限");
            Class<?> pdfType=Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Object pdf=pdfType.getMethod("load",File.class).invoke(null,file);
            try{
                int pages=((Number)pdfType.getMethod("getNumberOfPages").invoke(pdf)).intValue();if(pages==0)throw new java.io.IOException("PDF 没有页面");
                for(int i=0;i<pages;i++){
                    Object page=pdfType.getMethod("getPage",int.class).invoke(pdf,i),box=page.getClass().getMethod("getCropBox").invoke(page);
                    double width=((Number)box.getClass().getMethod("getWidth").invoke(box)).doubleValue()*200.0/72.0;
                    double height=((Number)box.getClass().getMethod("getHeight").invoke(box)).doubleValue()*200.0/72.0;
                    if(!Double.isFinite(width)||!Double.isFinite(height)||width<1||height<1||width*height>4_000_000)throw new java.io.IOException("PDF 页面在默认分辨率下过大");
                }
            }
            finally{pdfType.getMethod("close").invoke(pdf);}
        }else if(extension.equals("svg")){
            if(Files.size(file.toPath())>32L*1024*1024)throw new java.io.IOException("SVG 文件超过 32 MB 上限");
            javax.xml.parsers.SAXParserFactory factory=javax.xml.parsers.SAXParserFactory.newInstance();factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            final boolean[] seen={false};try(InputStream input=Files.newInputStream(file.toPath())){factory.newSAXParser().parse(input,new org.xml.sax.helpers.DefaultHandler(){
                public void startElement(String uri,String local,String qName,org.xml.sax.Attributes attributes)throws org.xml.sax.SAXException{if(!seen[0]){seen[0]=true;if(!"svg".equals(local)&&!"svg".equals(qName))throw new org.xml.sax.SAXException("不是 SVG 文件");}}
            });}if(!seen[0])throw new java.io.IOException("SVG 文件没有根元素");
        }else if(extension.equals("dxf")){
            byte[] header=new byte[4096];int n;try(InputStream input=Files.newInputStream(file.toPath())){n=input.read(header);}
            if(n<=0||!new String(header,0,n,java.nio.charset.StandardCharsets.US_ASCII).contains("SECTION"))throw new java.io.IOException("DXF 头部无效");
        }else if(extension.equals("word")){
            if(Files.size(file.toPath())>8L*1024*1024)throw new java.io.IOException("WORD 文件超过 8 MB 上限");
            Class<?> jsonType=Class.forName("org.json.JSONObject");Object json=jsonType.getConstructor(String.class).newInstance(new String(Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));
            jsonType.getMethod("getString",String.class).invoke(json,"pageInfo");jsonType.getMethod("getJSONArray",String.class).invoke(json,"items");
            Object paragraphs=jsonType.getMethod("getJSONArray",String.class).invoke(json,"paragraph");
            if(((Number)paragraphs.getClass().getMethod("length").invoke(paragraphs)).intValue()==0)throw new java.io.IOException("WORD 文件没有段落");
        }
    }

    private void switchWhenUnlocked(Object target){
        try{
            if(!MachinePanelWorkflow.sourceLocked(app)){switchToLoadedNote(target);return;}
            javax.swing.Timer wait=new javax.swing.Timer(100,null);
            wait.addActionListener(event->{try{if(!MachinePanelWorkflow.sourceLocked(app)){wait.stop();switchToLoadedNote(target);}}catch(Exception failure){wait.stop();showOpenError(failure);}});
            wait.start();
        }catch(Exception error){showOpenError(error);}
    }
    private void switchToLoadedNote(Object target)throws Exception{
        modes.setSelectedItem(target);
        if(modes.getSelectedItem()!=target||field(app,"g")!=target)JOptionPane.showMessageDialog(frame,"文章已载入笔记，但当前模式未能切换。请稍后手动选择笔记模式。","打开文章",JOptionPane.WARNING_MESSAGE);
    }

    private void showOpenFailure(Object target){
        try{
            Object reason=target.getClass().getMethod("lastFailure").invoke(target);
            JOptionPane.showMessageDialog(frame,"打开文章失败："+String.valueOf(reason),"打开文件",JOptionPane.ERROR_MESSAGE);
        }catch(Exception error){throw new IllegalStateException(error);}
    }

    private static boolean isNote(Object mode){return mode!=null&&mode.getClass().getName().equals("com.kenjoy.kenjoycnc.processTabs.NoteTab");}
    private static Object field(Object owner,String name)throws Exception{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
}

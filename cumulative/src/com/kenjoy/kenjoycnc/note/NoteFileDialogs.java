package com.kenjoy.kenjoycnc.note;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Windows Common Item Dialog. All COM calls and releases belong to one STA. No Swing fallback. */
public final class NoteFileDialogs {
    private static final int CANCELLED=0x800704C7;
    private static final String SHELL_ITEM="{43826D1E-E718-42EE-BC55-A1E261C37BFE}";
    private NoteFileDialogs(){}
    public static File openArticle(Component parent,File current)throws IOException{return chooseArticle(parent,0,current);}
    public static File saveArticle(Component parent,File current)throws IOException{return chooseArticle(parent,1,current);}
    public static File importText(Component parent)throws IOException{return chooseArticle(parent,2,null);}
    public static File openFile(Component parent,String title,File current,String description,String... extensions)throws IOException {
        return chooseFile(parent,false,title,current,description,extensions);
    }
    public static File saveFile(Component parent,String title,File current,String description,String... extensions)throws IOException {
        return chooseFile(parent,true,title,current,description,extensions);
    }
    public static int showOpenDialog(JFileChooser chooser,Component parent){return showChooser(chooser,parent,false,null);}
    public static int showSaveDialog(JFileChooser chooser,Component parent){return showSaveDialog(chooser,parent,null);}
    public static int showSaveDialog(JFileChooser chooser,Component parent,String defaultExtension){return showChooser(chooser,parent,true,defaultExtension);}

    private static final class Request {
        boolean save,folders,multiple,strict;
        String title,extension,clientId;
        File current,folder;
        String[][] specs=new String[0][];
        FileFilter[] fileFilters=new FileFilter[0];
        int filterIndex=1;
    }
    private static final class Result {
        boolean approved;
        File[] files=new File[0];
        File folder;
        int filterIndex;
    }
    private static Request fileRequest(boolean save,String title,File current,String description,String[] extensions)throws IOException {
        Request r=new Request();r.save=save;r.title=title;r.current=current;
        // A relative current is a suggested filename, never a request to browse Program Files.
        if(current!=null&&current.isAbsolute())r.folder=current.isDirectory()?current:current.getParentFile();
        String[] ext=extensions==null?new String[0]:extensions;
        if(ext.length>0){r.extension=extension(ext[0]);r.strict=save;r.specs=new String[][]{{description==null?pattern(ext):description,pattern(ext)}};}
        else r.specs=new String[][]{{"所有文件 (*.*)","*.*"}};
        return r;
    }
    private static Request articleRequest(int kind,File current)throws IOException {
        boolean save=kind==1,importing=kind==2;
        Request r=fileRequest(save,importing?"导入 TXT / Word":save?"保存文章":"打开文章",current,"奎享文章 (*.kjnote)",new String[]{"kjnote"});
        r.strict=save;
        r.specs=importing?new String[][]{{"文章 TXT / Word (*.txt;*.doc;*.docx)","*.txt;*.doc;*.docx"},{"文本文件 (*.txt)","*.txt"},{"Word 文档 (*.doc;*.docx)","*.doc;*.docx"}}:
            save?r.specs:new String[][]{{"全部文章 (*.kjnote;*.txt;*.doc;*.docx)","*.kjnote;*.txt;*.doc;*.docx"},{"奎享文章 (*.kjnote)","*.kjnote"},{"文本文件 (*.txt)","*.txt"},{"Word 文档 (*.doc;*.docx)","*.doc;*.docx"}};
        r.clientId=importing?"{7BF0484C-C1AE-45DB-BC0C-C6E99AC00503}":save?"{7BF0484C-C1AE-45DB-BC0C-C6E99AC00502}":"{7BF0484C-C1AE-45DB-BC0C-C6E99AC00501}";
        if(save&&current==null)r.current=new File("文章.kjnote");return r;
    }
    private static String extension(String value)throws IOException {
        if(value==null)throw new IOException("文件扩展名为空");
        String s=value.trim();if(s.startsWith("*."))s=s.substring(2);else if(s.startsWith("."))s=s.substring(1);
        if(s.isEmpty()||s.matches(".*[\\\\/:*?; ,\"<>|].*"))throw new IOException("不支持的文件扩展名："+value);
        return s;
    }
    private static String pattern(String[] extensions)throws IOException {
        StringBuilder s=new StringBuilder();for(String ext:extensions){if(s.length()>0)s.append(';');s.append("*.").append(extension(ext));}return s.toString();
    }
    private static String[] customExtensions(FileFilter filter)throws IOException {
        // The installed L uses M(String[]); read only extension arrays, including inherited fields.
        String[] found=null;
        try {
            for(Class<?> type=filter.getClass();type!=null&&type!=FileFilter.class;type=type.getSuperclass())for(Field field:type.getDeclaredFields())if(field.getType()==String[].class){
                field.setAccessible(true);String[] value=(String[])field.get(filter);
                if(value!=null&&value.length>0){if(found!=null)throw new IOException("过滤器含多个扩展名列表："+filter.getClass().getName());found=value.clone();}
            }
        }catch(IllegalAccessException|RuntimeException e){throw new IOException("无法读取文件过滤器："+filter.getClass().getName(),e);}
        if(found!=null){pattern(found);return found;}
        String description=filter.getDescription();
        if(description!=null&&description.trim().matches("(?:\\*\\.[^,;\\s*?]+[,;]?)+")){
            String[] parts=description.trim().split("[,;]");for(int i=0;i<parts.length;i++)parts[i]=extension(parts[i]);return parts;
        }
        // b/g has no extension field; confirm its known description against accept().
        if(description!=null&&description.toLowerCase(Locale.ROOT).contains("shp")&&filter.accept(new File("x.shp"))&&!filter.accept(new File("x.txt")))return new String[]{"shp"};
        throw new IOException("不支持的原生文件过滤器："+filter.getClass().getName()+" ("+description+")");
    }
    private static Request chooserRequest(JFileChooser chooser,boolean save,String defaultExtension)throws IOException {
        Request r=new Request();r.save=save;r.title=chooser.getDialogTitle();r.current=chooser.getSelectedFile();r.folder=chooser.getCurrentDirectory();
        int mode=chooser.getFileSelectionMode();
        if(mode==JFileChooser.FILES_AND_DIRECTORIES)throw new IOException("Windows 原生窗口不支持同时选择文件和目录");
        r.folders=mode==JFileChooser.DIRECTORIES_ONLY;r.multiple=!save&&chooser.isMultiSelectionEnabled();
        if(r.folders)return r; // FOS_PICKFOLDERS must never be combined with SetFileTypes.
        List<String[]> specs=new ArrayList<String[]>();List<FileFilter> filters=new ArrayList<FileFilter>();
        FileFilter selected=chooser.getFileFilter(),all=chooser.getAcceptAllFileFilter();
        for(FileFilter filter:chooser.getChoosableFileFilters()){
            String p=filter==all?"*.*":pattern(filter instanceof FileNameExtensionFilter?((FileNameExtensionFilter)filter).getExtensions():customExtensions(filter));
            specs.add(new String[]{filter.getDescription()==null?p:filter.getDescription(),p});filters.add(filter);
            if(filter==selected)r.filterIndex=specs.size();
        }
        if(specs.isEmpty()){specs.add(new String[]{"所有文件 (*.*)","*.*"});filters.add(null);}
        if(defaultExtension!=null&&!defaultExtension.trim().isEmpty()){
            r.extension=extension(defaultExtension);
            if(selected==null||selected==all){
                FileFilter generated=new FileNameExtensionFilter("*."+r.extension,r.extension);
                // Old L appends its output suffix after return. Its unfiltered save must
                // therefore confirm a final filename in that format inside Windows.
                if(specs.size()==1&&(filters.get(0)==all||filters.get(0)==null)){specs.clear();filters.clear();r.strict=true;}
                specs.add(0,new String[]{generated.getDescription(),"*."+r.extension});filters.add(0,generated);r.filterIndex=1;
            }
        }else {
            String p=specs.get(r.filterIndex-1)[1];if(!p.equals("*.*"))r.extension=p.substring(2).split(";",2)[0];
        }
        r.specs=specs.toArray(new String[specs.size()][]);r.fileFilters=filters.toArray(new FileFilter[filters.size()]);return r;
    }
    private static int showChooser(final JFileChooser chooser,final Component parent,final boolean save,final String extension){
        try{return onEdt(()->{
            Request r=chooserRequest(chooser,save,extension);Result result=choose(parent,r);
            if(result.folder!=null)chooser.setCurrentDirectory(result.folder);
            if(result.filterIndex>0&&result.filterIndex<=r.fileFilters.length&&r.fileFilters[result.filterIndex-1]!=null)chooser.setFileFilter(r.fileFilters[result.filterIndex-1]);
            if(!result.approved)return JFileChooser.CANCEL_OPTION;
            if(chooser.isMultiSelectionEnabled())chooser.setSelectedFiles(result.files);
            chooser.setSelectedFile(result.files[0]);return JFileChooser.APPROVE_OPTION;
        });}catch(IOException|RuntimeException|LinkageError e){chooser.putClientProperty("NoteFileDialogs.error",e);return JFileChooser.ERROR_OPTION;}
    }
    private static File chooseFile(Component parent,boolean save,String title,File current,String description,String[] extensions)throws IOException {
        Request r=fileRequest(save,title,current,description,extensions);
        Result result=onEdt(()->choose(parent,r));return result.approved?result.files[0]:null;
    }
    private static File chooseArticle(Component parent,int kind,File current)throws IOException {
        Result result=onEdt(()->choose(parent,articleRequest(kind,current)));if(!result.approved)return null;
        File file=result.files[0];String name=file.getName().toLowerCase(Locale.ROOT);
        boolean nativeArticle=name.endsWith(".kjnote"),text=name.endsWith(".txt")||name.endsWith(".doc")||name.endsWith(".docx");
        if(kind==1?!nativeArticle:kind==2?!text:!(nativeArticle||text))throw new IOException(kind==1?"请选择 .kjnote 文章":kind==2?"请选择 TXT、DOC 或 DOCX 文章":"请选择 KJNOTE、TXT、DOC 或 DOCX 文章");
        return file;
    }
    private static <T>T onEdt(Callable<T> action)throws IOException {
        if(SwingUtilities.isEventDispatchThread())try{return action.call();}catch(IOException e){throw e;}catch(Exception e){throw new IOException("Windows 文件窗口失败",e);}
        FutureTask<T> task=new FutureTask<T>(action);SwingUtilities.invokeLater(task);return await(task);
    }
    private static <T>T await(FutureTask<T> task)throws IOException {
        boolean interrupted=false;
        try{for(;;)try{return task.get();}catch(InterruptedException e){interrupted=true;}catch(ExecutionException e){throw new IOException("Windows 文件窗口失败："+e.getCause(),e.getCause());}}
        finally{if(interrupted)Thread.currentThread().interrupt();}
    }
    private static boolean isOwnedBy(Window candidate,Window ancestor){
        for(Window w=candidate;w!=null;w=w.getOwner())if(w==ancestor)return true;
        return false;
    }
    private static Window rootOwner(Window w){
        Window root=w;
        while(root!=null&&root.getOwner()!=null)root=root.getOwner();
        return root;
    }
    private static boolean blocksWindow(Dialog modal,Window candidate){
        if(modal==null||!modal.isShowing()||!modal.isEnabled()||!modal.isModal())return false;
        if(isOwnedBy(candidate,modal))return false;
        Dialog.ModalityType type=modal.getModalityType();
        if(type==Dialog.ModalityType.DOCUMENT_MODAL)return rootOwner(candidate)==rootOwner(modal);
        return type==Dialog.ModalityType.APPLICATION_MODAL||type==Dialog.ModalityType.TOOLKIT_MODAL;
    }
    private static Window effectiveOwner(Component parent){
        Window owner=parent instanceof Window?(Window)parent:parent==null?null:SwingUtilities.getWindowAncestor(parent);
        if(owner!=null){
            Window active=KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if(active instanceof Dialog&&blocksWindow((Dialog)active,owner))return active;
            Window[] all=Window.getWindows();
            for(int i=all.length-1;i>=0;i--)if(all[i] instanceof Dialog&&blocksWindow((Dialog)all[i],owner))return all[i];
            if(!owner.isEnabled()&&active instanceof Dialog&&active.isShowing()&&active.isEnabled())return active;
        }
        return owner;
    }
    private static Result choose(Component parent,Request request)throws IOException {
        final Window owner=effectiveOwner(parent);
        final WinDef.HWND handle=owner!=null&&owner.isDisplayable()?new WinDef.HWND(Native.getComponentPointer(owner)):null;
        final SecondaryLoop loop=Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        FutureTask<Result> task=new FutureTask<Result>(()->nativeChoose(handle,request)){
            protected void done(){SwingUtilities.invokeLater(()->loop.exit());}
        };
        List<Window> disabled=new ArrayList<Window>();
        try {
            for(Window w:Window.getWindows())if(w.isShowing()&&w.isEnabled()){disabled.add(w);w.setEnabled(false);}
            Thread sta=new Thread(task,"note-native-file-dialog");sta.setDaemon(true);sta.start();loop.enter();return await(task);
        }finally {
            for(Window w:disabled)if(w.isDisplayable())w.setEnabled(true);
            if(owner!=null&&owner.isShowing()&&owner.isEnabled()){owner.toFront();owner.requestFocus();}
        }
    }
    public interface Shell extends StdCallLibrary {
        Shell INSTANCE=Native.loadLibrary("shell32",Shell.class);
        WinNT.HRESULT SHCreateItemFromParsingName(WString name,Pointer bind,Guid.GUID iid,PointerByReference item);
    }
    public static class Filter extends Structure {
        public WString name,pattern;
        protected List<String> getFieldOrder(){return Arrays.asList("name","pattern");}
    }
    private static final class ComObject extends PointerType {
        Filter[] filters=new Filter[0];
        ComObject(Pointer p){super(p);}
        int call(int slot,Object... args){Object[] all=new Object[args.length+1];all[0]=getPointer();System.arraycopy(args,0,all,1,args.length);return Function.getFunction(getPointer().getPointer(0).getPointer((long)slot*Pointer.SIZE),Function.ALT_CONVENTION).invokeInt(all);}
        void release(){call(2);}
        void checked(int slot,Object... args)throws IOException{check(call(slot,args));}
    }
    private static void check(int hr)throws IOException {if(hr<0)throw new IOException(String.format(Locale.ROOT,"HRESULT 0x%08X",hr));}
    // Retained for the existing reflection checks.
    private static ComObject createDialog(int kind,File current)throws IOException{return createDialog(articleRequest(kind,current));}
    private static ComObject createDialog(Request r)throws IOException {
        ComObject dialog=null;boolean save=r.save&&!r.folders;
        try {
            PointerByReference ref=new PointerByReference();
            check(Ole32.INSTANCE.CoCreateInstance(new Guid.GUID(save?"{C0B4E2F3-BA21-4773-8DBA-335EC946EB8B}":"{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}"),null,1,
                new Guid.GUID(save?"{42F85136-DB7E-439C-85F1-E4075D135FC8}":"{D57C7288-D4AD-4768-BE02-9D969532D960}"),ref).intValue());
            dialog=new ComObject(ref.getValue());IntByReference options=new IntByReference();dialog.checked(10,options);
            int flags=(options.getValue()&~(0x20|0x200|0x1000|0x2|0x4))|0x40|0x800|0x8;
            flags|=r.folders?0x20:save?0x2|(r.strict?0x4:0):0x1000;
            if(r.multiple)flags|=0x200;dialog.checked(9,flags);
            if(!r.folders&&r.specs.length>0){
                Filter[] filters=(Filter[])new Filter().toArray(r.specs.length);dialog.filters=filters;
                for(int i=0;i<filters.length;i++){filters[i].name=new WString(r.specs[i][0]);filters[i].pattern=new WString(r.specs[i][1]);filters[i].write();}
                dialog.checked(4,filters.length,filters[0].getPointer());dialog.checked(5,r.filterIndex);
            }
            if(r.title!=null)dialog.checked(17,new WString(r.title));
            if(r.clientId!=null)dialog.checked(24,new Guid.GUID(r.clientId));
            if(save&&r.extension!=null)dialog.checked(22,new WString(r.extension));
            if(r.current!=null&&!r.current.isDirectory())dialog.checked(15,new WString(r.current.getName()));
            if(r.folder!=null&&r.folder.isDirectory()){
                PointerByReference item=new PointerByReference();check(Shell.INSTANCE.SHCreateItemFromParsingName(new WString(r.folder.getAbsolutePath()),null,new Guid.GUID(SHELL_ITEM),item).intValue());
                ComObject directory=new ComObject(item.getValue());try{dialog.checked(12,directory.getPointer());}finally{directory.release();}
            }
            return dialog;
        }catch(IOException|RuntimeException|LinkageError error){if(dialog!=null)dialog.release();throw error;}
    }
    private static File path(ComObject item)throws IOException {
        PointerByReference p=new PointerByReference();item.checked(5,0x80058000,p);
        try{return new File(p.getValue().getWideString(0));}finally{Ole32.INSTANCE.CoTaskMemFree(p.getValue());}
    }
    private static File singleResult(ComObject dialog,int slot)throws IOException {
        PointerByReference p=new PointerByReference();dialog.checked(slot,p);ComObject item=new ComObject(p.getValue());
        try{return path(item);}finally{item.release();}
    }
    private static Result nativeChoose(WinDef.HWND owner,Request r)throws IOException {
        Ole32 ole=Ole32.INSTANCE;check(ole.CoInitializeEx(null,Ole32.COINIT_APARTMENTTHREADED|Ole32.COINIT_DISABLE_OLE1DDE).intValue());
        ComObject dialog=null;
        try {
            dialog=createDialog(r);int hr=dialog.call(3,owner);
            // Retain the native strings through Show, including the folder case (empty array).
            for(Filter filter:dialog.filters)filter.read();
            if(hr!=CANCELLED)check(hr);Result result=new Result();result.approved=hr!=CANCELLED;
            if(!r.folders){IntByReference index=new IntByReference();dialog.checked(6,index);result.filterIndex=index.getValue();}
            // A cancelled dialog may have no folder, e.g. cancellation during initialization.
            try{result.folder=singleResult(dialog,13);}catch(IOException unavailable){if(result.approved)throw unavailable;}
            if(!result.approved)return result;
            if(r.multiple){
                PointerByReference p=new PointerByReference();dialog.checked(27,p);ComObject items=new ComObject(p.getValue());
                try{
                    IntByReference count=new IntByReference();items.checked(7,count);result.files=new File[count.getValue()];
                    for(int i=0;i<result.files.length;i++){
                        PointerByReference selected=new PointerByReference();items.checked(8,i,selected);ComObject item=new ComObject(selected.getValue());
                        try{result.files[i]=path(item);}finally{item.release();}
                    }
                }finally{items.release();}
            }else result.files=new File[]{singleResult(dialog,20)};
            if(result.files.length==0)throw new IOException("Windows 文件窗口没有返回所选路径");return result;
        }finally{if(dialog!=null)dialog.release();ole.CoUninitialize();}
    }
}

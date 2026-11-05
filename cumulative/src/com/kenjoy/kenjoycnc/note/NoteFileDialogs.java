package com.kenjoy.kenjoycnc.note;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;

/** Windows Common Item Dialog. COM objects stay on one dedicated STA thread. */
public final class NoteFileDialogs {
    private NoteFileDialogs(){}
    public static File openArticle(Component parent,File current)throws IOException{return choose(parent,0,current);}
    public static File saveArticle(Component parent,File current)throws IOException{return choose(parent,1,current);}
    public static File importText(Component parent)throws IOException{return choose(parent,2,null);}

    private static File choose(Component parent,int kind,File current)throws IOException {
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("文件窗口必须在EDT打开");
        final Window owner=parent instanceof Window?(Window)parent:SwingUtilities.getWindowAncestor(parent);
        if(owner==null||!owner.isDisplayable())throw new IOException("文章窗口尚未准备好");
        final WinDef.HWND handle=new WinDef.HWND(Native.getComponentPointer(owner));
        final SecondaryLoop loop=Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        FutureTask<File> task=new FutureTask<File>(()->nativeChoose(handle,kind,current)){
            protected void done(){SwingUtilities.invokeLater(()->loop.exit());}
        };
        List<Window> disabled=new ArrayList<Window>();
        try {
            // Keep Swing dispatching/painting, but prevent edits or a second dialog while choosing.
            for(Window w:Window.getWindows())if(w.isShowing()&&w.isEnabled()){disabled.add(w);w.setEnabled(false);}
            Thread sta=new Thread(task,"note-native-file-dialog");sta.setDaemon(true);sta.start();loop.enter();
            return task.get();
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("文件窗口被中断",e);}
        catch(ExecutionException e){throw new IOException("Windows 文件窗口失败："+e.getCause().getMessage(),e.getCause());}
        finally {for(Window w:disabled)if(w.isDisplayable())w.setEnabled(true);if(owner.isShowing()){owner.toFront();owner.requestFocus();}}
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
        Filter[] filters;
        ComObject(Pointer p){super(p);}
        int call(int slot,Object... args){Object[] all=new Object[args.length+1];all[0]=getPointer();System.arraycopy(args,0,all,1,args.length);return Function.getFunction(getPointer().getPointer(0).getPointer((long)slot*Pointer.SIZE),Function.ALT_CONVENTION).invokeInt(all);}
        void release(){call(2);}
        void checked(int slot,Object... args)throws IOException{check(call(slot,args));}
    }
    private static void check(int hr)throws IOException {if(hr<0)throw new IOException(String.format(Locale.ROOT,"HRESULT 0x%08X",hr));}
    private static ComObject createDialog(int kind,File current)throws IOException {
        ComObject dialog=null;
        try {
            boolean save=kind==1,importing=kind==2;
            PointerByReference ref=new PointerByReference();
            check(Ole32.INSTANCE.CoCreateInstance(new Guid.GUID(save?"{C0B4E2F3-BA21-4773-8DBA-335EC946EB8B}":"{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}"),null,1,new Guid.GUID("{42F85136-DB7E-439C-85F1-E4075D135FC8}"),ref).intValue());
            dialog=new ComObject(ref.getValue());
            IntByReference options=new IntByReference();dialog.checked(10,options);
            // Filesystem paths, existing parent, no process-directory changes. Save confirms the final native filename.
            dialog.checked(9,options.getValue()|0x40|0x800|0x8|(save?0x2|0x4:0x1000));
            String[][] specs=importing?new String[][]{{"文章 TXT / Word (*.txt;*.doc;*.docx)","*.txt;*.doc;*.docx"},{"文本文件 (*.txt)","*.txt"},{"Word 文档 (*.doc;*.docx)","*.doc;*.docx"}}:
                save?new String[][]{{"奎享文章 (*.kjnote)","*.kjnote"}}:
                new String[][]{{"全部文章 (*.kjnote;*.txt;*.doc;*.docx)","*.kjnote;*.txt;*.doc;*.docx"},{"奎享文章 (*.kjnote)","*.kjnote"},{"文本文件 (*.txt)","*.txt"},{"Word 文档 (*.doc;*.docx)","*.doc;*.docx"}};
            Filter[] filters=(Filter[])new Filter().toArray(specs.length);
            dialog.filters=filters;
            for(int i=0;i<filters.length;i++){filters[i].name=new WString(specs[i][0]);filters[i].pattern=new WString(specs[i][1]);filters[i].write();}
            dialog.checked(4,filters.length,filters[0].getPointer());dialog.checked(5,1);
            dialog.checked(17,new WString(importing?"导入 TXT / Word":save?"保存文章":"打开文章"));
            dialog.checked(24,new Guid.GUID(importing?"{7BF0484C-C1AE-45DB-BC0C-C6E99AC00503}":save?"{7BF0484C-C1AE-45DB-BC0C-C6E99AC00502}":"{7BF0484C-C1AE-45DB-BC0C-C6E99AC00501}"));
            if(save)dialog.checked(22,new WString("kjnote"));
            if(save)dialog.checked(15,new WString(current==null?"文章.kjnote":current.getName()));
            File folder=current==null?null:current.getAbsoluteFile().getParentFile();
            if(folder!=null&&folder.isDirectory()) {
                PointerByReference item=new PointerByReference();check(Shell.INSTANCE.SHCreateItemFromParsingName(new WString(folder.getPath()),null,new Guid.GUID("{43826D1E-E718-42EE-BC55-A1E261C37BFE}"),item).intValue());
                ComObject directory=new ComObject(item.getValue());try{dialog.checked(12,directory.getPointer());}finally{directory.release();}
            }
            return dialog;
        }catch(IOException|RuntimeException|LinkageError error){if(dialog!=null)dialog.release();throw error;}
    }
    private static File nativeChoose(WinDef.HWND owner,int kind,File current)throws IOException {
        Ole32 ole=Ole32.INSTANCE;check(ole.CoInitializeEx(null,Ole32.COINIT_APARTMENTTHREADED|Ole32.COINIT_DISABLE_OLE1DDE).intValue());
        ComObject dialog=null;
        try {
            dialog=createDialog(kind,current);
            int hr=dialog.call(3,owner);
            // Keep native filter strings alive until Show has returned.
            for(Filter f:dialog.filters)f.read();
            if(hr==0x800704C7)return null;
            check(hr);PointerByReference item=new PointerByReference();dialog.checked(20,item);
            ComObject selected=new ComObject(item.getValue());PointerByReference path=new PointerByReference();
            try {selected.checked(5,0x80058000,path);try {
                File result=new File(path.getValue().getWideString(0));String name=result.getName().toLowerCase(Locale.ROOT);
                boolean nativeArticle=name.endsWith(".kjnote"),textArticle=name.endsWith(".txt")||name.endsWith(".doc")||name.endsWith(".docx");
                if(kind==1?!nativeArticle:kind==2?!textArticle:!(nativeArticle||textArticle))throw new IOException(kind==1?"请选择 .kjnote 文章":kind==2?"请选择 TXT、DOC 或 DOCX 文章":"请选择 KJNOTE、TXT、DOC 或 DOCX 文章");
                return result;
            }finally{ole.CoTaskMemFree(path.getValue());}}finally{selected.release();}
        }finally{if(dialog!=null)dialog.release();ole.CoUninitialize();}
    }
}

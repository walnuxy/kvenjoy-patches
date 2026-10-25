package com.kenjoy.uitrial;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/** Validates the whole settings archive before replacing any existing setting. */
public final class SettingsImportWorkflow {
    private static final long MAX_ARCHIVE=64L*1024*1024,MAX_ENTRY=16L*1024*1024;
    private static final int MAX_ENTRIES=4096;
    private SettingsImportWorkflow(){}

    public static void run(Object app){
        try{
            JFrame frame=(JFrame)app.getClass().getMethod("f").invoke(app);
            JMenuItem item=(JMenuItem)field(app,"F");
            File chosen=(File)Class.forName("com.kenjoy.kenjoycnc.L")
                .getMethod("a",Component.class,String.class,String.class,String[].class)
                .invoke(null,frame,"选择奎享设置文件","export_settings",new String[]{"zip"});
            if(chosen==null)return;
            File settings=(File)Class.forName("com.kenjoy.kenjoycnc.L").getMethod("a").invoke(null);
            Path root=settings.getCanonicalFile().toPath();
            item.setEnabled(false);
            new SwingWorker<Void,Void>(){
                protected Void doInBackground()throws Exception{importArchive(chosen.toPath(),root);return null;}
                protected void done(){item.setEnabled(true);try{get();Field skipSave=app.getClass().getDeclaredField("M");skipSave.setAccessible(true);skipSave.setBoolean(null,true);JOptionPane.showMessageDialog(frame,"设置已导入。请保存当前文章并重启软件，导入设置将在重启后生效。","导入设置",JOptionPane.INFORMATION_MESSAGE);}
                    catch(Exception error){Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();JOptionPane.showMessageDialog(frame,"导入失败："+(cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage()),"导入设置",JOptionPane.ERROR_MESSAGE);}}
            }.execute();
        }catch(Exception error){Throwable cause=error instanceof InvocationTargetException?((InvocationTargetException)error).getCause():error;JOptionPane.showMessageDialog(null,"导入失败："+(cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage()),"导入设置",JOptionPane.ERROR_MESSAGE);}
    }

    public static void importArchive(Path archive,Path settingsRoot)throws IOException{
        Path root=settingsRoot.toAbsolutePath().normalize();
        if(!Files.isDirectory(root)||Files.isSymbolicLink(root))throw new IOException("设置目录不存在或为符号链接");
        if(!Files.isRegularFile(archive)||Files.size(archive)>MAX_ARCHIVE)throw new IOException("设置 ZIP 不存在或过大");
        Path staging=Files.createTempDirectory(root.getParent(),".settings-import-");
        boolean preserveBackup=false;
        try{
            Map<Path,Path> staged=new LinkedHashMap<Path,Path>();long total=0;int count=0;
            try(InputStream file=Files.newInputStream(archive);ZipInputStream zip=new ZipInputStream(file)){
                for(ZipEntry entry;(entry=zip.getNextEntry())!=null;zip.closeEntry()){
                    if(++count>MAX_ENTRIES)throw new IOException("设置 ZIP 条目过多");
                    String name=entry.getName().replace('\\','/');
                    if(name.indexOf(':')>=0||name.startsWith("/")||name.indexOf('\0')>=0)throw new IOException("设置 ZIP 包含无效路径");
                    Path target=root.getParent().resolve(name).normalize();
                    if(!target.startsWith(root))throw new IOException("设置 ZIP 包含目录外路径："+name);
                    checkPath(root,target);
                    if(entry.isDirectory()){if(Files.exists(target)&&!Files.isDirectory(target))throw new IOException("目录与文件冲突："+name);continue;}
                    if(target.equals(root)||staged.containsKey(target)||Files.isDirectory(target))throw new IOException("设置 ZIP 条目重复或无效："+name);
                    Path stagedFile=staging.resolve("data-"+count);long entryBytes=0;byte[] buffer=new byte[8192];
                    try(java.io.OutputStream out=Files.newOutputStream(stagedFile)){
                        for(int n;(n=zip.read(buffer))!=-1;){total+=n;entryBytes+=n;if(total>MAX_ARCHIVE||entryBytes>MAX_ENTRY)throw new IOException("设置 ZIP 解压内容过大");out.write(buffer,0,n);}
                    }
                    staged.put(target,stagedFile);
                }
            }
            if(staged.isEmpty())throw new IOException("设置 ZIP 没有可导入的文件");
            Map<Path,Path> before=new LinkedHashMap<Path,Path>();long backupBytes=0;int backupIndex=0;
            for(Path target:staged.keySet()){
                checkPath(root,target);
                if(Files.exists(target)){
                    if(!Files.isRegularFile(target))throw new IOException("目标不是普通文件："+target.getFileName());
                    backupBytes+=Files.size(target);if(backupBytes>MAX_ARCHIVE)throw new IOException("现有设置备份过大");
                    Path backup=staging.resolve("before-"+(++backupIndex));Files.copy(target,backup);before.put(target,backup);
                }else before.put(target,null);
            }
            List<Path> changed=new ArrayList<Path>();
            try{
                for(Map.Entry<Path,Path> item:staged.entrySet()){
                    Path target=item.getKey();checkPath(root,target);changed.add(target);writeAtomic(target,item.getValue());
                }
            }catch(IOException|RuntimeException|Error error){
                for(int i=changed.size()-1;i>=0;i--){Path target=changed.get(i);try{Path old=before.get(target);if(old==null)Files.deleteIfExists(target);else restoreBackup(target,old);}catch(Exception restore){preserveBackup=true;error.addSuppressed(restore);}}
                if(preserveBackup){
                    StringBuilder map=new StringBuilder("导入回滚未完成；请先退出软件，再按下列映射恢复原文件。\n");
                    for(Map.Entry<Path,Path> item:before.entrySet())if(item.getValue()!=null&&Files.exists(item.getValue()))map.append(item.getValue()).append(" -> ").append(item.getKey()).append('\n');
                    try{Files.write(staging.resolve("RECOVERY.txt"),map.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(IOException recordFailure){error.addSuppressed(recordFailure);}
                    IOException incomplete=new IOException("设置导入回滚未完成；原文件备份保留在："+staging,error);
                    for(Throwable failure:error.getSuppressed())incomplete.addSuppressed(failure);
                    throw incomplete;
                }
                throw error;
            }
        }finally{
            if(!preserveBackup)try(java.util.stream.Stream<Path> files=Files.walk(staging)){
                for(Path path:(Iterable<Path>)files.sorted(java.util.Comparator.reverseOrder())::iterator)Files.deleteIfExists(path);
            }catch(IOException cleanup){System.err.println("SETTINGS_IMPORT_CLEANUP "+cleanup);}
        }
    }
    private static void checkPath(Path root,Path target)throws IOException{
        for(Path part=target;part!=null&&part.startsWith(root);part=part.getParent())if(Files.isSymbolicLink(part))throw new IOException("设置路径包含符号链接："+part.getFileName());
    }
    private static void writeAtomic(Path target,Path source)throws IOException{
        Files.createDirectories(target.getParent());Path temp=Files.createTempFile(target.getParent(),".settings-import-",".tmp");
        try{Files.copy(source,temp,StandardCopyOption.REPLACE_EXISTING);try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(temp);}
    }
    private static void restoreBackup(Path target,Path backup)throws IOException{
        try{Files.move(backup,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException unsupported){Files.move(backup,target,StandardCopyOption.REPLACE_EXISTING);}
    }
    private static Object field(Object owner,String name)throws Exception{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
}

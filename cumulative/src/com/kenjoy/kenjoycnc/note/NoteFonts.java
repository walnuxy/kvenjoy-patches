package com.kenjoy.kenjoycnc.note;

import com.kenjoy.kenjoycnc.L;
import com.kenjoy.kenjoycnc.fonts.v;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Local font catalogue. Resolve by content; basename alone is never an identity. */
public final class NoteFonts {
    public static final class Entry {
        public final File file;
        public final String label,folder;
        private String hash;
        private v loaded;
        private final long size,modified;
        Entry(File file,String label,String folder) {this.file=file;this.label=label;this.folder=folder;size=file.length();modified=file.lastModified();}
        private void unchanged() throws IOException {if(!file.isFile()||file.length()!=size||file.lastModified()!=modified)throw new IOException("字体文件已变化，请刷新字库后重试："+label);}
        private static String digest(byte[] bytes)throws Exception {StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format("%02x",b&255));return s.toString();}
        public synchronized String hash() throws Exception {
            unchanged();
            if(hash==null) {MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format("%02x",b&255));hash=s.toString();}
            unchanged();return hash;
        }
        public synchronized v font() throws Exception {
            unchanged();
            if(loaded==null) {byte[] bytes=Files.readAllBytes(file.toPath());unchanged();String actual=digest(bytes);if(hash!=null&&!hash.equals(actual))throw new IOException("字体内容已变化，请刷新字库："+label);hash=actual;v f=new v();f.f=file.getName();try(InputStream in=new ByteArrayInputStream(bytes)){f.c(in);}catch(Exception e){f.f();throw e;}if(f.d<=0){f.f();throw new IOException("字体尺寸无效："+label);}loaded=f;}
            return loaded;
        }
        public String toString() {return label+"  ["+folder+"]";}
    }
    private final Path root;
    private final List<Entry> entries;
    private final List<String> errors;
    private final com.kenjoy.kenjoycnc.fonts.FontCatalogue.Snapshot metadata;
    public NoteFonts(File directory) throws Exception {this(directory,null,true);}
    private NoteFonts(File directory,NoteFonts previous,boolean scan)throws Exception {
        root=directory.toPath().toAbsolutePath().normalize();
        com.kenjoy.kenjoycnc.fonts.FontCatalogue.Snapshot snapshot=scan?com.kenjoy.kenjoycnc.fonts.FontCatalogue.at(directory).refresh().get():com.kenjoy.kenjoycnc.fonts.FontCatalogue.at(directory).current();
        metadata=snapshot;
        Map<Path,Entry> old=new HashMap<>();if(previous!=null)for(Entry e:previous.entries)old.put(e.file.toPath(),e);
        List<Entry> good=new ArrayList<>();
        for(com.kenjoy.kenjoycnc.fonts.FontCatalogue.Entry f:snapshot.entries){
            Entry e=old.get(f.path);if(e!=null&&e.size==f.size&&e.modified==f.modified){good.add(e);continue;}
            String folder=root.relativize(f.path).getNameCount()>1?root.relativize(f.path).getName(0).toString():"未分类";
            good.add(new Entry(f.path.toFile(),f.header.b,folder));
        }
        entries=Collections.unmodifiableList(good);errors=snapshot.errors;
    }
    public NoteFonts refresh()throws Exception{return new NoteFonts(root.toFile(),this,true);}
    public NoteFonts updated()throws Exception{return com.kenjoy.kenjoycnc.fonts.FontCatalogue.at(root.toFile()).current()==metadata?this:new NoteFonts(root.toFile(),this,false);}
    public List<String> errors(){return errors;}
    public static NoteFonts installed() throws Exception { return new NoteFonts(L.c()); }
    public List<Entry> entries() {return entries;}
    public NoteDocument.FontRef ref(Entry e) throws Exception {return new NoteDocument.FontRef(root.relativize(e.file.toPath().toAbsolutePath()).toString().replace('\\','/'),e.hash(),e.label);}
    public Entry resolve(NoteDocument.FontRef ref) throws Exception {
        if(ref==null || ref.hash==null || !ref.hash.matches("[0-9a-f]{64}")) throw new IOException("缺少有效字体绑定");
        Path hint=root.resolve(ref.relativePath==null?"":ref.relativePath).normalize();
        if(hint.startsWith(root)) for(Entry e:entries) if(e.file.toPath().equals(hint) && e.hash().equals(ref.hash))return e;
        for(Entry e:entries) if(e.hash().equals(ref.hash))return e;
        throw new IOException("找不到文章使用的字体："+ref.name+"。请恢复字库或为相关字重新指定字体。");
    }
    public List<NoteDocument.FontRef> byNames(String[] names) throws Exception {
        List<NoteDocument.FontRef> result=new ArrayList<NoteDocument.FontRef>();
        if(names!=null)for(String name:names) {
            Entry found=null;
            for(Entry e:entries) if(e.file.getName().equals(name)) {
                if(found!=null && !found.hash().equals(e.hash()))throw new IOException("同名字体内容不同，请先整理字库："+name);
                found=e;
            }
            if(found==null)throw new IOException("找不到默认字体："+name);
            result.add(ref(found));
        }
        return result;
    }
    public void close() {for(Entry e:entries)if(e.loaded!=null)e.loaded.f();}
}

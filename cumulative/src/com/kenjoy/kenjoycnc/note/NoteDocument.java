package com.kenjoy.kenjoycnc.note;

import com.google.gson.Gson;
import com.kenjoy.kenjoycnc.data.PageInfo;
import com.kenjoy.kenjoycnc.data.WordContentFormat;
import com.kenjoy.kenjoycnc.data.FontsSetData;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Document state only; selection, Swing components and paths are never serialized. */
public final class NoteDocument {
    public static final int FORMAT = 1;
    public static final long MAX_DOCUMENT_BYTES = 16L * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 100_000;
    public int formatVersion = FORMAT;
    public long nextId = 1;
    public long generation = 0;
    public String text = "";
    public PageInfo page = new PageInfo();
    public WordContentFormat format = new WordContentFormat();
    public ShapeSettings shapes = new ShapeSettings();
    public List<Atom> atoms = new ArrayList<Atom>();
    public List<FontRef> pool = new ArrayList<FontRef>();
    public int[] chances;

    public static final class FontRef {
        public String relativePath, hash, name;
        public FontRef() {}
        public FontRef(String path, String sha, String label) { relativePath=path; hash=sha; name=label; }
    }
    public static final class Atom {
        public long id;
        public String text;
        public FontRef automatic, override;
        public float size=1, scaleX=1, scaleY=1, rotate=0, y=0, space=1;
        public boolean sampled;
        public boolean drawable() { return text.length()==1 && Character.getType(text.charAt(0))!=Character.FORMAT && !Character.isWhitespace(text.charAt(0)) && !Character.isSpaceChar(text.charAt(0)); }
    }
    public static final class ShapeSettings {
        public float baseLine=1, randomY=0, randomFirstPos=0, widthAdd=0, spaceWidthAdd=0, tabWidthAdd=0, simple=0;
        public float[] randomFontSize={1,1}, randomXScale={1,1}, randomYScale={1,1}, randomRotate={0,0}, randomSpace={1,1};
        public String chars="，,.。";
        public static ShapeSettings capture(FontsSetData source) {
            return new Gson().fromJson(new Gson().toJson(source),ShapeSettings.class);
        }
    }
    public NoteDocument copy() { return decode(encode()); }
    public String encode() { return new Gson().toJson(this); }
    public static NoteDocument decode(String json) {
        NoteDocument d=new Gson().fromJson(json,NoteDocument.class);
        if(d==null || d.formatVersion!=FORMAT || d.atoms==null || d.page==null || d.format==null || d.shapes==null || d.pool==null)
            throw new IllegalArgumentException("文章文档版本或内容不兼容");
        if(d.text==null||d.text.length()>MAX_TEXT_CHARS)throw new IllegalArgumentException("文章正文超过 10 万字符上限");
        Set<Long> ids=new HashSet<Long>(); StringBuilder text=new StringBuilder();long max=0;
        for(Atom a:d.atoms) {
            if(a==null || a.text==null || a.text.isEmpty() || a.id<=0 || !ids.add(a.id)) throw new IllegalArgumentException("文章字符身份无效");
            text.append(a.text);max=Math.max(max,a.id);
        }
        if(!text.toString().equals(d.text) || d.nextId<=max) throw new IllegalArgumentException("文章正文与字符记录不一致");
        return d;
    }
    public static String normalize(String s) { return s.replace("\r\n","\n").replace('\r','\n'); }
    /** Apply an exact editor change. Unaffected atoms retain identity; no content-based diff. */
    public void replace(int offset,int length,String inserted) {
        if(offset<0 || length<0 || offset+length>text.length()) throw new IllegalArgumentException("编辑位置无效");
        String value=normalize(inserted);
        if((long)text.length()-length+value.length()>MAX_TEXT_CHARS)throw new IllegalArgumentException("文章正文超过 10 万字符上限");
        Map<Integer,Atom> retained=new HashMap<Integer,Atom>();int pos=0;
        for(Atom a:atoms) {
            int end=pos+a.text.length();
            if(end<=offset) retained.put(pos,a);
            else if(pos>=offset+length && !(length==0 && pos<offset && end>offset)) retained.put(pos+value.length()-length,a);
            pos=end;
        }
        text=text.substring(0,offset)+value+text.substring(offset+length);
        List<Atom> result=new ArrayList<Atom>();
        for(int i=0;i<text.length();) {
            int start=i,cp=text.codePointAt(i);i+=Character.charCount(cp);
            while(i<text.length()) {
                int q=text.codePointAt(i),type=Character.getType(q);
                if(type==Character.NON_SPACING_MARK || type==Character.COMBINING_SPACING_MARK || type==Character.ENCLOSING_MARK || q==0xFE0F) i+=Character.charCount(q);
                else if(q==0x200D && i+1<text.length()) { i++;i+=Character.charCount(text.codePointAt(i)); }
                else break;
            }
            String part=text.substring(start,i);Atom a=retained.get(start);
            if(a==null || !part.equals(a.text)) {if(nextId<=0||nextId==Long.MAX_VALUE)throw new IllegalArgumentException("文章字符编号已耗尽");a=new Atom();a.id=nextId++;a.text=part;}
            result.add(a);
        }
        atoms=result;
    }
    public void rerandomize() {
        generation++;
        for(Atom a:atoms) if(a.override==null) { a.automatic=null;a.sampled=false; }
    }
    /** One pass over the whole article; preserve every unaffected glyph identity. */
    public void clearMissing(Set<Long> ids,boolean spaces) {
        List<Atom> kept=new ArrayList<Atom>();StringBuilder body=new StringBuilder();
        for(Atom a:atoms){
            if(ids.contains(a.id)){
                if(!spaces)continue;
                if(nextId<=0||nextId==Long.MAX_VALUE)throw new IllegalArgumentException("文章字符编号已耗尽");
                a=new Atom();a.id=nextId++;a.text=" ";
            }
            kept.add(a);body.append(a.text);
        }
        atoms=kept;text=body.toString();
    }
    public Atom atom(long id) { for(Atom a:atoms) if(a.id==id)return a;return null; }
    public void save(Path target) throws IOException {
        Path absolute=target.toAbsolutePath();Path parent=absolute.getParent();Files.createDirectories(parent);
        Path temp=parent.resolve(absolute.getFileName()+".tmp-"+Long.toHexString(System.nanoTime()));
        try {
            byte[] data=encode().getBytes(StandardCharsets.UTF_8);
            if(data.length>MAX_DOCUMENT_BYTES)throw new IOException("文章序列化后超过 16 MB 上限，无法保存");
            try(OutputStream out=Files.newOutputStream(temp,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {out.write(data);}
            try {Files.move(temp,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e) {Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
    }
    public static NoteDocument load(Path file) throws IOException {
        if(Files.size(file)>MAX_DOCUMENT_BYTES)throw new IOException("文章文件超过 16 MB 上限");
        try(InputStream in=Files.newInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] chunk=new byte[8192];int n;long total=0;
            while((n=in.read(chunk))!=-1){total+=n;if(total>MAX_DOCUMENT_BYTES)throw new IOException("文章文件超过 16 MB 上限");out.write(chunk,0,n);}
            return decode(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }
    }
}

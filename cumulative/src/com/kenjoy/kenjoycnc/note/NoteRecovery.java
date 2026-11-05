package com.kenjoy.kenjoycnc.note;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kenjoy.kenjoycnc.L;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import java.util.UUID;

/** Local recovery is separate from the article. Only confirmed editor state belongs here. */
public final class NoteRecovery {
    public enum ClosePolicy { AUTO_RECOVER, ASK_SAVE }
    public static final class Snapshot {
        public int formatVersion=1;
        public String documentId=UUID.randomUUID().toString();
        public NoteDocument document;
        /** Absolute association only; recovery never writes this path. */
        public String file;
        /** Last actively saved semantic state, if the editor has one. */
        public String savedState;
        public boolean dirty,editing;
        public long revision;
        public int anchor,dot,page,scrollX,scrollY;
        public float processingAngle;
    }
    private static final String RECOVERY="recovery.json",PREFERENCES="preferences.properties";
    private static final long MAX_BYTES=NoteDocument.MAX_DOCUMENT_BYTES*3+65536;
    private NoteRecovery(){}
    public static Path directory(){return new File(L.b(),"note-editor").toPath();}
    public static ClosePolicy closePolicy()throws IOException{return closePolicy(directory());}
    public static void setClosePolicy(ClosePolicy policy)throws IOException{setClosePolicy(directory(),policy);}
    public static Snapshot load()throws IOException{return load(directory());}
    public static void save(Snapshot snapshot)throws IOException{save(directory(),snapshot);}
    public static void clear()throws IOException{clear(directory());}

    public static synchronized ClosePolicy closePolicy(Path directory)throws IOException{
        Path file=directory.resolve(PREFERENCES);if(!Files.exists(file))return ClosePolicy.AUTO_RECOVER;
        Properties values=new Properties();try(InputStream in=new ByteArrayInputStream(read(file,4096))){values.load(in);}
        try{return ClosePolicy.valueOf(values.getProperty("closePolicy",ClosePolicy.AUTO_RECOVER.name()));}
        catch(IllegalArgumentException error){throw new IOException("关闭处理设置无效，请重新选择",error);}
    }
    public static synchronized void setClosePolicy(Path directory,ClosePolicy policy)throws IOException{
        if(policy==null)throw new IllegalArgumentException("缺少关闭处理方式");
        atomic(directory.resolve(PREFERENCES),("closePolicy="+policy.name()+"\n").getBytes(StandardCharsets.US_ASCII));
    }
    public static synchronized Snapshot load(Path directory)throws IOException{
        Path file=directory.resolve(RECOVERY);if(!Files.exists(file))return null;
        try{
            JsonObject envelope=new JsonParser().parse(new String(read(file,MAX_BYTES),StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement document=envelope.remove("document");
            if(document==null||document.isJsonNull())throw new IllegalArgumentException("恢复稿缺少文章");
            String encoded=document.toString();
            Snapshot snapshot=new Gson().fromJson(envelope,Snapshot.class);
            snapshot.document=NoteDocument.decode(encoded);
            validate(snapshot,encoded);return snapshot;
        }catch(RuntimeException error){throw new IOException("恢复稿无效，已保留原文件："+file,error);}
    }
    public static synchronized void save(Path directory,Snapshot snapshot)throws IOException{
        try{
            if(snapshot==null||snapshot.document==null)throw new IllegalArgumentException("恢复稿缺少文章");
            String encoded=snapshot.document.encode();validate(snapshot,encoded);
            // Serialize only metadata here. Serializing Snapshot directly expands every shared font policy.
            JsonObject envelope=new Gson().toJsonTree(metadata(snapshot)).getAsJsonObject();
            envelope.add("document",new JsonParser().parse(encoded));
            byte[] bytes=envelope.toString().getBytes(StandardCharsets.UTF_8);
            if(bytes.length>MAX_BYTES)throw new IOException("恢复稿超过大小上限");
            atomic(directory.resolve(RECOVERY),bytes);
        }catch(RuntimeException error){throw new IOException("无法保存恢复稿："+error.getMessage(),error);}
    }
    public static synchronized void clear(Path directory)throws IOException{Files.deleteIfExists(directory.resolve(RECOVERY));}
    private static Snapshot metadata(Snapshot source){
        Snapshot value=new Snapshot();value.formatVersion=source.formatVersion;value.documentId=source.documentId;
        value.file=source.file;value.savedState=source.savedState;value.dirty=source.dirty;value.editing=source.editing;
        value.revision=source.revision;value.anchor=source.anchor;value.dot=source.dot;value.page=source.page;
        value.scrollX=source.scrollX;value.scrollY=source.scrollY;value.processingAngle=source.processingAngle;return value;
    }
    private static void validate(Snapshot snapshot,String encoded){
        if(snapshot==null||snapshot.formatVersion!=1||snapshot.document==null||snapshot.documentId==null||snapshot.documentId.isEmpty())throw new IllegalArgumentException("缺少文章或不支持的恢复稿版本");
        snapshot.document.validate();
        if(encoded.getBytes(StandardCharsets.UTF_8).length>NoteDocument.MAX_DOCUMENT_BYTES)throw new IllegalArgumentException("文章超过大小上限");
        if(snapshot.savedState!=null&&snapshot.savedState.getBytes(StandardCharsets.UTF_8).length>NoteDocument.MAX_DOCUMENT_BYTES)throw new IllegalArgumentException("保存状态超过大小上限");
        int length=snapshot.document.text.length();
        if(snapshot.anchor<0||snapshot.anchor>length||snapshot.dot<0||snapshot.dot>length||snapshot.page<0||snapshot.scrollX<0||snapshot.scrollY<0||snapshot.revision<0||!Float.isFinite(snapshot.processingAngle))throw new IllegalArgumentException("恢复稿位置无效");
        if(snapshot.file!=null&&!Paths.get(snapshot.file).isAbsolute())throw new IllegalArgumentException("文章关联必须为绝对路径");
    }
    private static byte[] read(Path file,long limit)throws IOException{
        if(Files.size(file)>limit)throw new IOException("恢复文件超过大小上限："+file.getFileName());
        try(InputStream in=Files.newInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int count;long total=0;
            while((count=in.read(buffer))!=-1){total+=count;if(total>limit)throw new IOException("恢复文件超过大小上限");out.write(buffer,0,count);}return out.toByteArray();
        }
    }
    private static void atomic(Path target,byte[] data)throws IOException{
        Path file=target.toAbsolutePath(),parent=file.getParent();Files.createDirectories(parent);
        Path temporary=Files.createTempFile(parent,file.getFileName()+"-",".tmp");
        try{
            try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.WRITE)){
                ByteBuffer buffer=ByteBuffer.wrap(data);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
            }
            try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException error){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
}

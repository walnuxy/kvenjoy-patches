package com.kenjoy.kenjoycnc.note;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Bounded UTF-8 JSON import/export for reusable forbidden-zone templates. */
public final class NoteForbiddenZoneTemplateIO {
    public static final int MAX_BYTES=1024*1024;
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();

    private NoteForbiddenZoneTemplateIO(){}

    private static JsonObject object(JsonElement value,String path)throws IOException{
        if(value==null||!value.isJsonObject())throw new IOException("禁写区模板缺少有效对象："+path);
        return value.getAsJsonObject();
    }
    private static JsonArray array(JsonObject object,String field,String path)throws IOException{
        JsonElement value=object.get(field);
        if(value==null||!value.isJsonArray())throw new IOException("禁写区模板缺少有效数组："+path+"."+field);
        return value.getAsJsonArray();
    }
    private static void require(JsonObject object,String field,String path)throws IOException{
        if(!object.has(field)||object.get(field).isJsonNull())throw new IOException("禁写区模板缺少必需字段："+path+"."+field);
    }
    private static void validateJsonShape(String json)throws IOException{
        JsonObject root=object(new JsonParser().parse(json),"template");
        require(root,"schemaVersion","template");require(root,"sourcePageWidthMm","template");require(root,"sourcePageHeightMm","template");require(root,"zones","template");
        JsonObject zones=object(root.get("zones"),"template.zones");
        require(zones,"schemaVersion","template.zones");
        JsonArray rules=array(zones,"rules","template.zones"),exceptions=array(zones,"exceptions","template.zones");
        for(int i=0;i<rules.size();i++){
            String path="template.zones.rules["+i+"]";JsonObject rule=object(rules.get(i),path);
            require(rule,"id",path);require(rule,"name",path);require(rule,"x",path);require(rule,"y",path);require(rule,"width",path);require(rule,"height",path);require(rule,"clearanceMm",path);
            require(rule,"pageMode",path);require(rule,"customPages",path);require(rule,"mirrorEven",path);
            JsonElement mirrorEven=rule.get("mirrorEven");
            if(!mirrorEven.isJsonPrimitive()||!mirrorEven.getAsJsonPrimitive().isBoolean())throw new IOException("禁写区模板字段必须为布尔值："+path+".mirrorEven");
        }
        for(int i=0;i<exceptions.size();i++){
            String path="template.zones.exceptions["+i+"]";JsonObject exception=object(exceptions.get(i),path);
            require(exception,"page",path);require(exception,"disabledRuleIds",path);require(exception,"additionalZones",path);
            JsonArray additional=array(exception,"additionalZones",path);
            for(int j=0;j<additional.size();j++){
                String rectPath=path+".additionalZones["+j+"]";JsonObject rect=object(additional.get(j),rectPath);
                require(rect,"id",rectPath);require(rect,"name",rectPath);require(rect,"x",rectPath);require(rect,"y",rectPath);require(rect,"width",rectPath);require(rect,"height",rectPath);require(rect,"clearanceMm",rectPath);
            }
        }
    }

    public static byte[] encode(NoteForbiddenZones.Template template)throws IOException{
        if(template==null)throw new IOException("禁写区模板为空");
        try{template.validate();}catch(RuntimeException invalid){throw new IOException("禁写区模板数据无效："+invalid.getMessage(),invalid);}
        byte[] data=GSON.toJson(template,NoteForbiddenZones.Template.class).getBytes(StandardCharsets.UTF_8);
        if(data.length>MAX_BYTES)throw new IOException("禁写区模板超过 1 MB 上限");
        return data;
    }

    public static NoteForbiddenZones.Template decode(byte[] data)throws IOException{
        if(data==null||data.length==0)throw new IOException("禁写区模板文件为空");
        if(data.length>MAX_BYTES)throw new IOException("禁写区模板超过 1 MB 上限");
        final String json;
        try{json=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();}
        catch(CharacterCodingException invalid){throw new IOException("禁写区模板不是有效的 UTF-8 文件",invalid);}
        JsonReader reader=new JsonReader(new StringReader(json));reader.setLenient(false);
        try{
            NoteForbiddenZones.Template template=GSON.fromJson(reader,NoteForbiddenZones.Template.class);
            if(template==null)throw new IOException("禁写区模板内容为空");
            if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("禁写区模板末尾存在多余内容");
            validateJsonShape(json);
            try{template.validate();}catch(RuntimeException invalid){throw new IOException("禁写区模板数据无效："+invalid.getMessage(),invalid);}
            return template;
        }catch(IOException error){throw error;}
        catch(RuntimeException invalid){throw new IOException("禁写区模板 JSON 格式无效",invalid);}
        finally{reader.close();}
    }

    public static NoteForbiddenZones.Template load(Path file)throws IOException{
        if(file==null||!Files.isRegularFile(file))throw new IOException("禁写区模板文件不存在或不是普通文件");
        if(Files.size(file)>MAX_BYTES)throw new IOException("禁写区模板超过 1 MB 上限");
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(InputStream input=Files.newInputStream(file)){
            byte[] buffer=new byte[8192];int count,total=0;
            while((count=input.read(buffer))!=-1){total+=count;if(total>MAX_BYTES)throw new IOException("禁写区模板超过 1 MB 上限");output.write(buffer,0,count);}
        }
        return decode(output.toByteArray());
    }

    public static void save(Path file,NoteForbiddenZones.Template template)throws IOException{
        if(file==null)throw new IOException("请指定禁写区模板保存位置");
        byte[] data=encode(template);Path absolute=file.toAbsolutePath().normalize();Path parent=absolute.getParent();
        if(parent==null||absolute.getFileName()==null)throw new IOException("禁写区模板保存路径无效");
        Files.createDirectories(parent);Path temporary=Files.createTempFile(parent,".forbidden-zone-",".tmp");
        try{
            try(OutputStream output=Files.newOutputStream(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){output.write(data);}
            try{Files.move(temporary,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,absolute,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
}

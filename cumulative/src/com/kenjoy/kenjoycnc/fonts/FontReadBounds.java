package com.kenjoy.kenjoycnc.fonts;

import java.io.*;

/** Validate serialized sizes before the original binary reader allocates arrays. */
public final class FontReadBounds {
    private FontReadBounds() {}
    // Five modified-UTF fields fit below 320KiB; allow ample format overhead.
    public static final int MAX_HEADER_BYTES=1024*1024;
    public static final int MAX_COORDINATES=1024*1024;
    public static int headerLength(int length)throws IOException{
        if(length<=0||length>MAX_HEADER_BYTES)throw new IOException("字体元数据长度无效："+length);
        return length;
    }
    public static int coordinateLength(int length)throws IOException{
        if(length<0||length>MAX_COORDINATES||(length&1)!=0)throw new IOException("字体坐标数量无效："+length);
        return length;
    }
    public static int flagLength(int length,float[] coordinates)throws IOException{
        if(coordinates==null||length<0||length!=coordinates.length/2)throw new IOException("字体笔画标记数量与坐标不匹配："+length);
        return length;
    }
    public static void preview(v font,InputStream stream,File file)throws Exception{
        try(InputStream owned=stream){font.b(owned);}
        catch(Exception failure){System.err.println("FONT_LOAD_ERROR phase=word-tab-preview file="+file+" cause="+failure);throw failure;}
    }
}

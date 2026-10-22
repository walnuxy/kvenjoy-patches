package com.kenjoy.kenjoycnc.fonts;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.imageio.ImageIO;

/** Exercise the original application's list renderer, including its coordinate transform. */
public final class FontPreviewChecks {
    static void layout(Component c) {
        if(c instanceof Container) { ((Container)c).doLayout(); for(Component child:((Container)c).getComponents()) layout(child); }
    }
    public static void main(String[] args) throws Exception {
        java.util.List<Path> paths = new ArrayList<Path>();
        try(java.util.stream.Stream<Path> walk=Files.walk(Paths.get(args[0]))) {
            walk.filter(p->(args.length>2 || p.getFileName().toString().startsWith("Plot-")) && p.toString().endsWith(".gfont")).sorted().forEach(paths::add);
        }
        BufferedImage sheet=new BufferedImage(1300,((paths.size()+1)/2)*100,BufferedImage.TYPE_INT_RGB);
        Graphics2D output=sheet.createGraphics();
        java.util.List<String> failures=new ArrayList<String>();
        int index=0;
        for(Path p:paths) {
            v font=new v();font.b(Files.newInputStream(p));
            ListCellRenderer renderer=(ListCellRenderer)Class.forName("com.kenjoy.kenjoycnc.l.a").newInstance();
            Component cell=renderer.getListCellRendererComponent(new JList(),font,0,false,false);
            cell.setSize(650,100);layout(cell);
            BufferedImage tile=new BufferedImage(650,100,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=tile.createGraphics();cell.paint(g);g.dispose();
            int minX=100,minY=100,maxX=-1,maxY=-1,pixels=0;
            for(int y=2;y<98;y++) for(int x=2;x<98;x++) {
                int rgb=tile.getRGB(x,y);
                if(((rgb>>16)&255)<80 && ((rgb>>8)&255)<80 && (rgb&255)<80) {
                    minX=Math.min(x,minX);maxX=Math.max(x,maxX);minY=Math.min(y,minY);maxY=Math.max(y,maxY);pixels++;
                }
            }
            boolean chinese=p.toString().contains("中文");
            boolean english=p.toString().contains("英文");
            if((chinese || english) && (pixels<50 || maxX-minX<10 || maxY-minY<20 || minY>60)) failures.add(font.b+" clipped/empty preview: "+pixels+" pixels, bounds "+minX+","+minY+".."+maxX+","+maxY);
            if(chinese && font.b()<6000) failures.add(font.b+" insufficient Chinese glyphs");
            if(args.length==2 && (font.c.length()>160 || font.c.contains("\n"))) failures.add(font.b+" oversized description");
            output.drawImage(tile,(index%2)*650,(index/2)*100,null);index++;
            System.out.println(font.b+" glyphs="+font.b()+" previewPixels="+pixels);
        }
        output.dispose();ImageIO.write(sheet,"png",Paths.get(args[1]).toFile());
        if(!failures.isEmpty()) throw new AssertionError(failures.toString());
        if(args.length==2 && index!=70) throw new AssertionError("Expected 70 fonts, got "+index);
        System.out.println("PASS original renderer: "+index+" fonts");System.exit(0);
    }
}

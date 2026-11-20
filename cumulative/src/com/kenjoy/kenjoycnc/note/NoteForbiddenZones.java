package com.kenjoy.kenjoycnc.note;

import com.google.gson.Gson;
import java.util.*;

/** Persistent, page-aware rectangular areas in which no pen-down path may be generated. */
public final class NoteForbiddenZones {
    public static final int SCHEMA_VERSION=1;
    public static final int MAX_RULES=256,MAX_EXCEPTIONS=10000,MAX_CUSTOM_PAGES=10000,MAX_EXCEPTION_ZONES=10000,MAX_PAGE_NUMBER=100000;
    public static final float DEFAULT_CLEARANCE_MM=.5f;

    public int schemaVersion=SCHEMA_VERSION;

    public enum PageMode {
        ALL("全部页面"),ODD("奇数页"),EVEN("偶数页"),CUSTOM("指定页面");
        private final String label;
        PageMode(String value){label=value;}
        public String toString(){return label;}
    }

    public static class Rect {
        public String id=UUID.randomUUID().toString();
        public String name="禁写区";
        public float x,y,width,height,clearanceMm=DEFAULT_CLEARANCE_MM;
        public Rect(){}
        public Rect(String label,float left,float top,float w,float h,float clearance){
            name=label;x=left;y=top;width=w;height=h;clearanceMm=clearance;
        }
        public Rect copy(){Rect value=new Rect(name,x,y,width,height,clearanceMm);value.id=id;return value;}
        public String toString(){return name+"  ·  "+String.format(Locale.ROOT,"%.1f × %.1f mm",width,height);}
    }

    public static final class Rule extends Rect {
        public PageMode pageMode=PageMode.ALL;
        public List<Integer> customPages=new ArrayList<Integer>();
        /** Mirror the rectangle horizontally on even pages, using the physical page width. */
        public boolean mirrorEven;
        public Rule(){}
        public Rule(String label,float left,float top,float w,float h,float clearance){super(label,left,top,w,h,clearance);}
        public Rule copy(){Rule value=new Rule(name,x,y,width,height,clearanceMm);value.id=id;value.pageMode=pageMode;value.customPages=customPages==null?null:new ArrayList<Integer>(customPages);value.mirrorEven=mirrorEven;return value;}
        public boolean appliesTo(int page){
            if(page<=0)return false;
            if(pageMode==PageMode.ALL)return true;
            if(pageMode==PageMode.ODD)return (page&1)==1;
            if(pageMode==PageMode.EVEN)return (page&1)==0;
            return customPages!=null&&customPages.contains(page);
        }
        public String toString(){return name+"  ·  "+pageMode;}
    }

    public static final class PageException {
        public int page;
        public List<String> disabledRuleIds=new ArrayList<String>();
        public List<Rect> additionalZones=new ArrayList<Rect>();
        public PageException copy(){PageException value=new PageException();value.page=page;value.disabledRuleIds=disabledRuleIds==null?null:new ArrayList<String>(disabledRuleIds);if(additionalZones==null)value.additionalZones=null;else{value.additionalZones=new ArrayList<Rect>();for(Rect rect:additionalZones)value.additionalZones.add(rect==null?null:rect.copy());}return value;}
    }

    public static final class ResolvedZone {
        public final String id,name;
        public final float x,y,width,height,clearanceMm;
        ResolvedZone(String id,String name,float x,float y,float width,float height,float clearance){this.id=id;this.name=name;this.x=x;this.y=y;this.width=width;this.height=height;clearanceMm=clearance;}
        public float left(){return x-clearanceMm;}
        public float top(){return y-clearanceMm;}
        public float right(){return x+width+clearanceMm;}
        public float bottom(){return y+height+clearanceMm;}
    }

    public static final class Template {
        public int schemaVersion=SCHEMA_VERSION;
        public float sourcePageWidthMm,sourcePageHeightMm;
        public NoteForbiddenZones zones=new NoteForbiddenZones();
        public Template(){}
        public Template(float width,float height,NoteForbiddenZones source){sourcePageWidthMm=width;sourcePageHeightMm=height;zones=source==null?null:source.copy();}
        public void validate(){
            if(schemaVersion!=SCHEMA_VERSION||!finite(sourcePageWidthMm)||!finite(sourcePageHeightMm)||sourcePageWidthMm<=0||sourcePageHeightMm<=0||zones==null)
                throw new IllegalArgumentException("禁写区模板版本或页面尺寸无效");
            zones.validate(sourcePageWidthMm,sourcePageHeightMm);
        }
        public NoteForbiddenZones convert(float targetWidth,float targetHeight,boolean scaleToPage){
            validate();NoteForbiddenZones result=zones.copy();
            if(!finite(targetWidth)||!finite(targetHeight)||targetWidth<=0||targetHeight<=0)throw new IllegalArgumentException("当前页面尺寸无效");
            if(scaleToPage){
                float sx=targetWidth/sourcePageWidthMm,sy=targetHeight/sourcePageHeightMm,sm=Math.max(sx,sy);
                for(Rule rule:result.rules){rule.x*=sx;rule.width*=sx;rule.y*=sy;rule.height*=sy;rule.clearanceMm*=sm;}
                for(PageException exception:result.exceptions)if(exception.additionalZones!=null)for(Rect rect:exception.additionalZones){rect.x*=sx;rect.width*=sx;rect.y*=sy;rect.height*=sy;rect.clearanceMm*=sm;}
            }
            result.validate(targetWidth,targetHeight);return result;
        }
    }

    public List<Rule> rules=new ArrayList<Rule>();
    public List<PageException> exceptions=new ArrayList<PageException>();

    public NoteForbiddenZones copy(){
        NoteForbiddenZones value=new NoteForbiddenZones();value.schemaVersion=schemaVersion;
        if(rules!=null)for(Rule rule:rules)value.rules.add(rule==null?null:rule.copy());
        if(exceptions!=null)for(PageException exception:exceptions)value.exceptions.add(exception==null?null:exception.copy());
        return value;
    }
    public boolean isEmpty(){return (rules==null||rules.isEmpty())&&(exceptions==null||exceptions.isEmpty());}
    public boolean hasData(){return !isEmpty();}
    public String key(){return new Gson().toJson(this);}
    public String pageKey(int page,float pageWidthMm,float pageHeightMm){
        StringBuilder value=new StringBuilder();for(ResolvedZone zone:resolve(page,pageWidthMm,pageHeightMm))
            value.append(zone.id).append(':').append(zone.x).append(',').append(zone.y).append(',').append(zone.width).append(',').append(zone.height).append(',').append(zone.clearanceMm).append(';');
        return value.toString();
    }
    public void addForPage(int page,float x,float y,float width,float height){
        if(page<=0||page>MAX_PAGE_NUMBER)throw new IllegalArgumentException("页面编号无效");
        if(rules==null)rules=new ArrayList<Rule>();
        Rule rule=new Rule("禁写区 "+(rules.size()+1),x,y,width,height,DEFAULT_CLEARANCE_MM);rule.pageMode=PageMode.CUSTOM;rule.customPages.add(page);rules.add(rule);
    }
    public void removeRule(String id){
        if(rules!=null)rules.removeIf(rule->rule!=null&&Objects.equals(rule.id,id));
        if(exceptions!=null)for(Iterator<PageException> iterator=exceptions.iterator();iterator.hasNext();){PageException exception=iterator.next();if(exception==null)continue;if(exception.disabledRuleIds!=null)exception.disabledRuleIds.remove(id);if((exception.disabledRuleIds==null||exception.disabledRuleIds.isEmpty())&&(exception.additionalZones==null||exception.additionalZones.isEmpty()))iterator.remove();}
    }
    public PageException exception(int page){if(exceptions!=null)for(PageException value:exceptions)if(value!=null&&value.page==page)return value;return null;}
    public void setException(int page,Collection<String> disabled){
        if(page<=0||page>MAX_PAGE_NUMBER)throw new IllegalArgumentException("例外页面编号无效");
        PageException existing=exception(page);List<String> ids=new ArrayList<String>();if(disabled!=null)for(String id:disabled)if(id!=null&&!ids.contains(id))ids.add(id);
        if(ids.isEmpty()&&(existing==null||existing.additionalZones==null||existing.additionalZones.isEmpty())){if(existing!=null)exceptions.remove(existing);return;}
        if(existing==null){existing=new PageException();existing.page=page;exceptions.add(existing);}
        existing.disabledRuleIds=ids;
    }
    public void addExceptionZone(int page,Rect rect){
        if(page<=0||page>MAX_PAGE_NUMBER||rect==null)throw new IllegalArgumentException("单页例外区域无效");
        PageException value=exception(page);if(value==null){value=new PageException();value.page=page;exceptions.add(value);}
        if(value.additionalZones==null)value.additionalZones=new ArrayList<Rect>();value.additionalZones.add(rect.copy());
    }
    public void removeExceptionZone(int page,String id){
        PageException value=exception(page);if(value==null||value.additionalZones==null)return;
        value.additionalZones.removeIf(rect->rect!=null&&Objects.equals(rect.id,id));
        if((value.disabledRuleIds==null||value.disabledRuleIds.isEmpty())&&value.additionalZones.isEmpty())exceptions.remove(value);
    }
    public List<ResolvedZone> resolve(int page,float pageWidthMm,float pageHeightMm){
        if(page<=0)return Collections.emptyList();Set<String> disabled=new HashSet<String>();PageException exception=exception(page);
        if(exception!=null&&exception.disabledRuleIds!=null)disabled.addAll(exception.disabledRuleIds);
        List<ResolvedZone> result=new ArrayList<ResolvedZone>();
        if(rules!=null)for(Rule rule:rules)if(rule!=null&&rule.appliesTo(page)&&!disabled.contains(rule.id)){
            float x=rule.x;if(rule.mirrorEven&&(page&1)==0)x=pageWidthMm-rule.x-rule.width;
            result.add(new ResolvedZone(rule.id,rule.name,x,rule.y,rule.width,rule.height,rule.clearanceMm));
        }
        if(exception!=null&&exception.additionalZones!=null)for(Rect rect:exception.additionalZones)if(rect!=null)
            result.add(new ResolvedZone(rect.id,rect.name,rect.x,rect.y,rect.width,rect.height,rect.clearanceMm));
        return result;
    }
    public void validate(float pageWidthMm,float pageHeightMm){
        if(schemaVersion!=SCHEMA_VERSION||rules==null||exceptions==null||rules.size()>MAX_RULES||exceptions.size()>MAX_EXCEPTIONS)throw new IllegalArgumentException("禁写区规则数量或版本无效");
        boolean hasRect=!rules.isEmpty();for(PageException exception:exceptions)if(exception!=null&&exception.additionalZones!=null&&!exception.additionalZones.isEmpty())hasRect=true;
        if(hasRect&&(!finite(pageWidthMm)||!finite(pageHeightMm)||pageWidthMm<=0||pageHeightMm<=0))throw new IllegalArgumentException("禁写区所在页面尺寸无效");
        Set<String> ids=new HashSet<String>();int customPageCount=0,exceptionZoneCount=0;for(Rule rule:rules){if(rule==null)throw new IllegalArgumentException("禁写区规则为空");validateRect(rule,pageWidthMm,pageHeightMm,ids);if(rule.pageMode==null)throw new IllegalArgumentException("禁写区页面规则无效");
            if(rule.pageMode==PageMode.CUSTOM){if(rule.customPages==null||rule.customPages.isEmpty())throw new IllegalArgumentException("自定义页面不能为空");Set<Integer> pages=new HashSet<Integer>();for(Integer page:rule.customPages)if(page==null||page<=0||page>MAX_PAGE_NUMBER||!pages.add(page))throw new IllegalArgumentException("自定义页面编号无效或重复");customPageCount+=pages.size();}
            else if(rule.customPages==null||!rule.customPages.isEmpty())throw new IllegalArgumentException("非自定义页面规则不得包含指定页码");
            if(customPageCount>MAX_CUSTOM_PAGES)throw new IllegalArgumentException("自定义页面总数过多");
        }
        Set<Integer> pages=new HashSet<Integer>();for(PageException exception:exceptions){
            if(exception==null||exception.page<=0||exception.page>MAX_PAGE_NUMBER||!pages.add(exception.page))throw new IllegalArgumentException("单页例外编号无效或重复");
            if(exception.disabledRuleIds==null||exception.additionalZones==null)throw new IllegalArgumentException("单页例外数据不完整");
            Set<String> disabled=new HashSet<String>();for(String id:exception.disabledRuleIds){if(id==null||!disabled.add(id)||rules.stream().noneMatch(rule->Objects.equals(rule.id,id)))throw new IllegalArgumentException("单页例外引用了不存在或重复的规则");}
            exceptionZoneCount+=exception.additionalZones.size();if(exceptionZoneCount>MAX_EXCEPTION_ZONES)throw new IllegalArgumentException("单页例外区域过多");
            for(Rect rect:exception.additionalZones){if(rect==null)throw new IllegalArgumentException("单页例外区域为空");validateRect(rect,pageWidthMm,pageHeightMm,ids);}
        }
    }
    private static void validateRect(Rect rect,float pageWidthMm,float pageHeightMm,Set<String> ids){
        if(rect.id==null||rect.id.trim().isEmpty()||!ids.add(rect.id)||rect.name==null||rect.name.trim().isEmpty()||rect.name.length()>80)
            throw new IllegalArgumentException("禁写区身份或名称无效");
        float right=rect.x+rect.width,bottom=rect.y+rect.height;
        if(!finite(rect.x)||!finite(rect.y)||!finite(rect.width)||!finite(rect.height)||!finite(rect.clearanceMm)||!finite(right)||!finite(bottom)||rect.width<.1f||rect.height<.1f||rect.clearanceMm<0||rect.clearanceMm>25)
            throw new IllegalArgumentException("禁写区坐标、尺寸或安全边距无效");
        if(rect.x<0||rect.y<0||right>pageWidthMm+.001f||bottom>pageHeightMm+.001f)
            throw new IllegalArgumentException("禁写区必须位于纸张范围内");
    }
    private static boolean finite(float value){return !Float.isNaN(value)&&!Float.isInfinite(value);}
}

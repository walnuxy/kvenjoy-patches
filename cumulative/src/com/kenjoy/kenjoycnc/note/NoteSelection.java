package com.kenjoy.kenjoycnc.note;

import java.util.*;

public final class NoteSelection {
    private final LinkedHashSet<Long> ids=new LinkedHashSet<Long>();
    public boolean multiple;
    public Set<Long> ids() {return Collections.unmodifiableSet(ids);}
    public Set<Long> capture() {return new LinkedHashSet<Long>(ids);}
    public void clear() {ids.clear();}
    public void click(Long id,boolean ctrl) {
        if(id==null){if(!multiple&&!ctrl)ids.clear();return;}
        if(multiple||ctrl){if(!ids.remove(id))ids.add(id);}else{ids.clear();ids.add(id);}
    }
    public void add(Collection<Long> values,boolean ctrl) {if(!multiple&&!ctrl)ids.clear();ids.addAll(values);}
    public void retain(NoteDocument d) {Set<Long> valid=new HashSet<Long>();for(NoteDocument.Atom a:d.atoms)valid.add(a.id);ids.retainAll(valid);}
}

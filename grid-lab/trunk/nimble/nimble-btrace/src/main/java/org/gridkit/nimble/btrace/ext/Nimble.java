package org.gridkit.nimble.btrace.ext;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.java.btrace.api.extensions.BTraceExtension;

import org.gridkit.nimble.btrace.ext.model.PointSample;
import org.gridkit.nimble.btrace.ext.model.ScalarSample;
import org.gridkit.nimble.btrace.ext.model.SpanSample;

@BTraceExtension
public class Nimble {
    private static ConcurrentMap<String, ScriptStore> scriptStores = new ConcurrentHashMap<String, ScriptStore>();
    
    protected static Collection<ScriptStore> getScriptStores(Collection<String> scriptClasses) {        
        Map<String, ScriptStore> result = new HashMap<String, ScriptStore>();
        
        result.putAll(scriptStores);
        result.keySet().retainAll(scriptClasses);
        
        return result.values();
    }
    
    public static SampleStore newSampleStore(String name, int capacity) {
        String scriptClass = getScriptClass();
        
        scriptStores.putIfAbsent(scriptClass, new ScriptStore(scriptClass));
        
        return scriptStores.get(scriptClass).add(name, capacity);
    }
    
    // TODO find more correct way to do it
    private static String getScriptClass() {
        StackTraceElement[] stackTrace = (new Exception()).getStackTrace();
        
        String clazz = stackTrace[2].getClassName();
        
        int index = clazz.lastIndexOf('$');
        
        return clazz.substring(0, index);
    }

    public static void sample(String key, SampleStore store, Number value) {
        ScalarSample sample = new ScalarSample();
        
        sample.setKey(key);
        sample.setValue(value);
        
        store.add(sample);
    }
    
    public static void sample(String key, SampleStore store, Number value, long timestamp) {
        PointSample sample = new PointSample();
        
        sample.setKey(key);
        sample.setValue(value);
        sample.setTimestamp(timestamp);
        
        store.add(sample);
    }

    public static void sample(String key, SampleStore store, Number value, long startTimestamp, long finishTimestamp) {
        SpanSample sample = new SpanSample();
        
        sample.setKey(key);
        sample.setValue(value);
        sample.setStartTimestamp(startTimestamp);
        sample.setFinishTimestamp(finishTimestamp);
        
        store.add(sample);
    }
}
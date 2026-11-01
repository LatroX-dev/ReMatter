package dev.latrox.rematter.automation;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import dev.latrox.rematter.automation.AutomationPort.Resource;

/** Exact vectors of independently measured plain item counts, fluid volumes and matter quantities. */
public final class ResourceAmounts {
    public static final int MAX_RESOURCES=256;
    private ResourceAmounts() {}
    public static Map<Resource,Long> copy(Map<Resource,Long> source) {
        Objects.requireNonNull(source);
        if(source.size()>MAX_RESOURCES)throw new IllegalArgumentException("Too many resource kinds");
        var result=new TreeMap<Resource,Long>();
        source.forEach((key,value)->{Objects.requireNonNull(key);if(value==null||value<=0)throw new IllegalArgumentException("Invalid exact amount");result.put(key,value);});
        return Collections.unmodifiableMap(result);
    }
    public static Map<Resource,Long> multiply(Map<Resource,Long> source,int batches) {
        if(batches<0||batches>OrderLedger.MAX_BATCHES)throw new IllegalArgumentException("Invalid batch count");
        if(batches==0)return Map.of();
        var result=new TreeMap<Resource,Long>();copy(source).forEach((key,value)->result.put(key,Math.multiplyExact(value,batches)));return copy(result);
    }
}

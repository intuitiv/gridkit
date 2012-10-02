package org.gridkit.nimble.sensor;

import static org.gridkit.nimble.util.StringOps.F;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.gridkit.nimble.util.Pair;
import org.gridkit.nimble.util.SetOps;
import org.hyperic.sigar.ProcCpu;
import org.hyperic.sigar.SigarException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
public class ProcCpuSensor extends IntervalMeasureSensor<List<IntervalMeasure<ProcCpu>>, Map<Long, Pair<Long, ProcCpu>>> {
    private static final Logger log = LoggerFactory.getLogger(ProcCpuSensor.class);
    
    private static long MIN_MEASURE_INTERVAL_MS = 3;
    
    private PidProvider pidProvider;
    private boolean refreshPids;
    
    private transient Set<Long> pids;

    public ProcCpuSensor(PidProvider pidProvider, long measureInterval, TimeUnit unit, boolean refreshPids) {
        super(Math.max(unit.toMillis(measureInterval), TimeUnit.SECONDS.toMillis(MIN_MEASURE_INTERVAL_MS)));
        this.pidProvider = pidProvider;
    }
    
    public ProcCpuSensor(PidProvider pidProvider, long measureIntervalS, boolean refreshPids) {
        this(pidProvider, measureIntervalS, TimeUnit.SECONDS, refreshPids);
    }
    
    public ProcCpuSensor(PidProvider pidProvider) {
        this(pidProvider, MIN_MEASURE_INTERVAL_MS, false);
    }

    @Override
    protected Map<Long, Pair<Long, ProcCpu>> getState() {
        Map<Long, Pair<Long, ProcCpu>> result = new HashMap<Long, Pair<Long, ProcCpu>>();
        
        for (Long pid : getPids()) {
            try {
                result.put(pid, Pair.newPair(System.nanoTime(), getSigar().getProcCpu(pid)));
            } catch (SigarException e) {
                log.error(F("Error while getting processes CPU usage for pid '%d'", pid), e);
                invalidate(pid);
            }
        }
        
        return result;
    }

    @Override
    protected List<IntervalMeasure<ProcCpu>> getMeasure(Map<Long, Pair<Long, ProcCpu>> leftState, Map<Long, Pair<Long, ProcCpu>> rightState) {
        List<IntervalMeasure<ProcCpu>> result = new ArrayList<IntervalMeasure<ProcCpu>>();
        
        for (Long pid : SetOps.<Long>intersection(leftState.keySet(), rightState.keySet())) {
            IntervalMeasure<ProcCpu> measure = new IntervalMeasure<ProcCpu>();
            
            Pair<Long, ProcCpu> left = leftState.get(pid);
            Pair<Long, ProcCpu> right = rightState.get(pid);
            
            measure.setLeftTsNs(left.getA());
            measure.setLeftState(left.getB());
            
            measure.setRightTsNs(right.getA());
            measure.setRightState(right.getB());

            result.add(measure);
        }
        
        return result;
    }
    
    private Collection<Long> getPids() {
        if (pids == null || refreshPids) {
            pids = new HashSet<Long>(pidProvider.getPids());
        }
        
        return pids;
    }
    
    private void invalidate(Long pid) {
        pids.remove(pid);
    }

    @Override
    public String toString() {
        return F("%s[%s]", ProcCpuSensor.class.getSimpleName(), pidProvider.toString());
    }
}
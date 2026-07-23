package com.asterix.service;

import com.asterix.decoder.DecodeResult;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Cumulative, thread-safe counters for the live dashboard. Updated from the
 * processing thread(s); read from the WebSocket broadcaster.
 */
@Service
public class StatsService {

    private final AtomicLong filesProcessed = new AtomicLong();
    private final LongAdder blocksReceived = new LongAdder();
    private final LongAdder blocksDecoded  = new LongAdder();
    private final LongAdder blocksError    = new LongAdder();
    private final LongAdder bytesProcessed = new LongAdder();
    private final LongAdder plotsDecoded   = new LongAdder();

    private final Map<Integer, LongAdder> perCategory = new ConcurrentHashMap<>();
    private final Map<Integer, LongAdder> perSourceReceived = new ConcurrentHashMap<>();
    private final Map<Integer, LongAdder> perSourceProcessed = new ConcurrentHashMap<>();

    public void addFileResult(DecodeResult r) {
        filesProcessed.incrementAndGet();
        blocksReceived.add(r.totalBlocks);
        blocksDecoded.add(r.okBlocks);
        blocksError.add(r.errorBlocks);
        bytesProcessed.add(r.bytes);
        plotsDecoded.add(r.plots.size());
        r.perCategory.forEach((k, v) -> bump(perCategory, k, v));
        r.perSourceReceived.forEach((k, v) -> bump(perSourceReceived, k, v));
        r.perSourceProcessed.forEach((k, v) -> bump(perSourceProcessed, k, v));
    }

    private void bump(Map<Integer, LongAdder> map, int key, long delta) {
        map.computeIfAbsent(key, k -> new LongAdder()).add(delta);
    }

    public void reset() {
        filesProcessed.set(0);
        blocksReceived.reset(); blocksDecoded.reset(); blocksError.reset();
        bytesProcessed.reset(); plotsDecoded.reset();
        perCategory.clear(); perSourceReceived.clear(); perSourceProcessed.clear();
    }

    // --- accessors used to build a snapshot ---
    public long files()   { return filesProcessed.get(); }
    public long received(){ return blocksReceived.sum(); }
    public long decoded() { return blocksDecoded.sum(); }
    public long errors()  { return blocksError.sum(); }
    public long bytes()   { return bytesProcessed.sum(); }
    public long plots()   { return plotsDecoded.sum(); }

    public Map<Integer, Long> categorySnapshot()      { return flatten(perCategory); }
    public Map<Integer, Long> sourceReceivedSnapshot(){ return flatten(perSourceReceived); }
    public Map<Integer, Long> sourceProcessedSnapshot(){ return flatten(perSourceProcessed); }

    private Map<Integer, Long> flatten(Map<Integer, LongAdder> m) {
        Map<Integer, Long> out = new ConcurrentHashMap<>();
        m.forEach((k, v) -> out.put(k, v.sum()));
        return out;
    }
}

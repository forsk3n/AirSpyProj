package com.asterix.service;

import com.asterix.decoder.Cat048Plot;
import com.asterix.geo.GeoUtil;
import com.asterix.geo.Radar;
import com.asterix.geo.RadarRegistry;
import com.asterix.model.FlightRecord;
import com.asterix.repo.FlightRecordRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains one {@link FlightRecord} per aircraft. Plots update an in-memory
 * cache; the cache is flushed to the database in batches for throughput.
 */
@Service
public class TrackManager {

    private final FlightRecordRepository repo;
    private final RadarRegistry radars;
    private final EnrichmentService enrichment;

    // icao -> live record
    private final Map<String, FlightRecord> active = new ConcurrentHashMap<>();
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();

    public TrackManager(FlightRecordRepository repo, RadarRegistry radars, EnrichmentService enrichment) {
        this.repo = repo;
        this.radars = radars;
        this.enrichment = enrichment;
    }

    @PostConstruct
    void warmUp() {
        // Resume active tracks from a previous run.
        for (FlightRecord f : repo.findTop500ByOrderByLastUpdatedDesc()) {
            if (f.getState() == FlightRecord.State.ACTIVE && f.getTrackKey() != null)
                active.putIfAbsent(f.getTrackKey(), f);
        }
    }

    /** Fold a single plot into its aircraft's record. */
    public void apply(Cat048Plot plot) {
        String key = plot.identityKey();
        if (key == null) return; // anonymous primary blip — decoded & counted, but not aggregated

        FlightRecord f = active.computeIfAbsent(key, k -> {
            FlightRecord created = repo.findFirstByTrackKeyAndState(k, FlightRecord.State.ACTIVE)
                    .orElseGet(FlightRecord::new);
            created.setTrackKey(k);
            created.setIcaoAddress(plot.icaoAddress);
            if (created.getStartTod() == null) created.setStartTod(plot.timeOfDay);
            enrichment.enrich(created); // fills country/type/model when a source is wired in
            return created;
        });

        f.setSourceType(FlightRecord.SourceType.valueOf(plot.sourceType().name()));
        if (plot.trackNumber != null) f.setTrackNumber(plot.trackNumber);
        if (plot.icaoAddress != null) f.setIcaoAddress(plot.icaoAddress);
        if (plot.mode3A != null)   f.setMode3A(plot.mode3A);
        if (plot.callsign != null) f.setCallsign(plot.callsign);
        if (plot.flightLevel != null) f.setFlightLevel(plot.flightLevel);

        if (plot.hasPolar) {
            Radar radar = radars.find(plot.sac, plot.sic);
            if (radar != null) {
                double[] ll = GeoUtil.toLatLon(radar, plot.rhoRaw, plot.thetaRaw, plot.flightLevel);
                f.setLatitude(round(ll[0]));
                f.setLongitude(round(ll[1]));
            }
        }

        if (f.getStartTod() == null || plot.timeOfDay < f.getStartTod()) f.setStartTod(plot.timeOfDay);
        f.setEndTod(plot.timeOfDay);
        f.setPlotCount(f.getPlotCount() + 1);
        f.setState(FlightRecord.State.ACTIVE);
        f.setLastUpdated(Instant.now());
        dirty.add(key);
    }

    /** Persist everything touched since the last flush. */
    @Transactional
    public void flush() {
        if (dirty.isEmpty()) return;
        List<FlightRecord> batch = new ArrayList<>();
        for (String key : dirty) {
            FlightRecord f = active.get(key);
            if (f != null) batch.add(f);
        }
        repo.saveAll(batch);
        dirty.clear();
    }

    /** Mark stale tracks inactive and drop them from the live cache. */
    @Transactional
    public int expire(long inactivitySeconds) {
        Instant cutoff = Instant.now().minusSeconds(inactivitySeconds);
        int n = repo.markInactiveBefore(cutoff);
        active.entrySet().removeIf(e -> e.getValue().getLastUpdated() != null
                && e.getValue().getLastUpdated().isBefore(cutoff));
        return n;
    }

    public int activeCount() { return active.size(); }

    private static Double round(double v) { return Math.round(v * 1e5) / 1e5; }
}

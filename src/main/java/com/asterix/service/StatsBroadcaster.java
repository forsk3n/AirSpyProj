package com.asterix.service;

import com.asterix.decoder.DecodeResult;
import com.asterix.geo.RadarRegistry;
import com.asterix.model.FlightRecord;
import com.asterix.repo.FlightRecordRepository;
import com.asterix.web.Dtos;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/** Builds dashboard snapshots once per second and pushes them to clients. */
@Service
public class StatsBroadcaster {

    private final StatsService stats;
    private final ProcessingService processing;
    private final TrackManager tracks;
    private final RadarRegistry radars;
    private final FlightRecordRepository flights;
    private final SimpMessagingTemplate ws;

    private long lastBytes = 0, lastBlocks = 0, lastTs = System.nanoTime();

    public StatsBroadcaster(StatsService stats, ProcessingService processing, TrackManager tracks,
                            RadarRegistry radars, FlightRecordRepository flights,
                            SimpMessagingTemplate ws) {
        this.stats = stats;
        this.processing = processing;
        this.tracks = tracks;
        this.radars = radars;
        this.flights = flights;
        this.ws = ws;
    }

    @Scheduled(fixedRate = 1000)
    public void push() {
        ws.convertAndSend("/topic/stats", buildStats());
        ws.convertAndSend("/topic/flights", buildFlights());
    }

    public Dtos.Stats buildStats() {
        long now = System.nanoTime();
        double dt = Math.max(1e-6, (now - lastTs) / 1e9);
        long bytes = stats.bytes();
        long blocks = stats.received();
        double mbPerSec = (bytes - lastBytes) / 1e6 / dt;
        double blocksPerSec = (blocks - lastBlocks) / dt;
        lastBytes = bytes; lastBlocks = blocks; lastTs = now;

        Map<String, Long> perCat = new TreeMap<>();
        stats.categorySnapshot().forEach((cat, n) -> perCat.put(String.format("CAT%03d", cat), n));

        Map<Integer, Long> recv = stats.sourceReceivedSnapshot();
        Map<Integer, Long> proc = stats.sourceProcessedSnapshot();
        List<Dtos.SourceStat> sources = new ArrayList<>();
        for (Integer key : new TreeSet<>(recv.keySet())) {
            sources.add(new Dtos.SourceStat(DecodeResult.sacOf(key), DecodeResult.sicOf(key),
                    recv.getOrDefault(key, 0L), proc.getOrDefault(key, 0L)));
        }

        return new Dtos.Stats(
                processing.isRunning(), processing.folder(),
                processing.lastFile(), processing.lastError(),
                stats.files(), stats.received(), stats.decoded(), stats.errors(),
                stats.plots(), tracks.activeCount(), radars.size(),
                bytes, bytes / 1e6, blocksPerSec, mbPerSec,
                perCat, sources);
    }

    public List<Dtos.Flight> buildFlights() {
        List<Dtos.Flight> out = new ArrayList<>();
        for (FlightRecord f : flights.findTop500ByOrderByLastUpdatedDesc()) {
            out.add(toDto(f));
        }
        return out;
    }

    public static Dtos.Flight toDto(FlightRecord f) {
        return new Dtos.Flight(
                f.getId(), f.getTrackKey(),
                f.getSourceType() == null ? null : f.getSourceType().name(), f.getTrackNumber(),
                f.getIcaoAddress(), f.getCallsign(), f.getMode3A(),
                f.getCountry(), f.getAircraftType(), f.getAircraftModel(),
                f.getLatitude(), f.getLongitude(), f.getFlightLevel(),
                hms(f.getStartTod()), hms(f.getEndTod()),
                f.getState() == null ? null : f.getState().name(), f.getPlotCount());
    }

    private static String hms(Double secondsOfDay) {
        if (secondsOfDay == null) return null;
        int s = (int) Math.floor(secondsOfDay);
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}

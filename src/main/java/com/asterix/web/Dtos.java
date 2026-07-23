package com.asterix.web;

import java.util.List;
import java.util.Map;

/** Transport objects for the dashboard. */
public final class Dtos {
    private Dtos() {}

    public record SourceStat(int sac, int sic, long received, long processed) {}

    public record Stats(
            boolean running,
            String folder,
            String lastFile,
            String lastError,
            long files,
            long received,
            long decoded,
            long errors,
            long plots,
            int activeTracks,
            int radarCount,
            long bytes,
            double mbProcessed,
            double blocksPerSec,
            double mbPerSec,
            Map<String, Long> perCategory,
            List<SourceStat> perSource
    ) {}

    public record Flight(
            Long id,
            String key,
            String sourceType,
            Integer trackNumber,
            String icao,
            String callsign,
            String mode3A,
            String country,
            String aircraftType,
            String aircraftModel,
            Double lat,
            Double lon,
            Double flightLevel,
            String startTime,
            String endTime,
            String state,
            long plotCount
    ) {}

    public record FolderRequest(String path) {}
}

package com.asterix.service;

import com.asterix.decoder.AsterixDecoder;
import com.asterix.decoder.DecodeResult;
import com.asterix.model.ProcessedFile;
import com.asterix.repo.ProcessedFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives the pipeline: while running, it repeatedly picks the oldest unprocessed
 * file in the watched folder, decodes it, folds plots into tracks, and records
 * the file as done. A polling model (rather than a filesystem watcher) keeps it
 * robust to files that are still being written and to network shares.
 */
@Service
public class ProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);

    private final AsterixDecoder decoder;
    private final DirectoryScanService scanner;
    private final TrackManager tracks;
    private final StatsService stats;
    private final ProcessedFileRepository processedRepo;

    @Value("${asterix.inactivity-timeout-seconds:30}")
    private long inactivityTimeout;

    private final AtomicReference<Path> folder = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile String lastFile = null;
    private volatile String lastError = null;

    public ProcessingService(AsterixDecoder decoder, DirectoryScanService scanner,
                             TrackManager tracks, StatsService stats,
                             ProcessedFileRepository processedRepo) {
        this.decoder = decoder;
        this.scanner = scanner;
        this.tracks = tracks;
        this.stats = stats;
        this.processedRepo = processedRepo;
    }

    // ---- control ----
    public synchronized void start(String path) {
        Path dir = Paths.get(path);
        if (!Files.isDirectory(dir))
            throw new IllegalArgumentException("Not a directory: " + path);
        folder.set(dir.toAbsolutePath());
        lastError = null;
        running.set(true);
        log.info("Processing started on {}", dir.toAbsolutePath());
    }

    public synchronized void stop() {
        running.set(false);
        log.info("Processing stopped");
    }

    public boolean isRunning() { return running.get(); }
    public String folder() { Path p = folder.get(); return p == null ? null : p.toString(); }
    public String lastFile() { return lastFile; }
    public String lastError() { return lastError; }

    // ---- main loop (poll every second) ----
    @Scheduled(fixedDelayString = "${asterix.poll-interval-ms:1000}")
    public void tick() {
        if (!running.get() || !busy.compareAndSet(false, true)) return;
        try {
            Path dir = folder.get();
            if (dir == null) return;
            for (DirectoryScanService.Candidate c : scanner.pending(dir)) {
                if (!running.get()) break;
                processOne(c);
            }
        } catch (Exception e) {
            lastError = e.getMessage();
            log.warn("Scan/process error", e);
        } finally {
            busy.set(false);
        }
    }

    private void processOne(DirectoryScanService.Candidate c) {
        String abs = c.path().toAbsolutePath().toString();
        try {
            byte[] bytes = Files.readAllBytes(c.path());
            DecodeResult r = decoder.decode(bytes);
            for (var plot : r.plots) tracks.apply(plot);
            tracks.flush();
            stats.addFileResult(r);

            ProcessedFile pf = new ProcessedFile(abs, c.size(), c.created());
            pf.setProcessedAt(Instant.now());
            pf.setBlocks(r.totalBlocks);
            pf.setErrors(r.errorBlocks);
            processedRepo.save(pf);

            lastFile = c.path().getFileName().toString();
            log.info("Processed {} — {} blocks, {} plots, {} errors",
                    lastFile, r.totalBlocks, r.plots.size(), r.errorBlocks);
        } catch (Exception e) {
            lastError = "Failed on " + abs + ": " + e.getMessage();
            log.error("Failed processing {}", abs, e);
            // Still record it so a bad file does not block the queue forever.
            ProcessedFile pf = new ProcessedFile(abs, c.size(), c.created());
            pf.setProcessedAt(Instant.now());
            pf.setErrors(-1);
            processedRepo.save(pf);
        }
    }

    // ---- periodically retire stale tracks ----
    @Scheduled(fixedDelayString = "${asterix.expiry-interval-ms:5000}")
    public void expireTracks() {
        try {
            int n = tracks.expire(inactivityTimeout);
            if (n > 0) log.debug("Marked {} tracks inactive", n);
        } catch (Exception e) {
            log.debug("expire error: {}", e.getMessage());
        }
    }
}

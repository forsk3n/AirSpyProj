package com.asterix.web;

import com.asterix.service.DirectoryScanService;
import com.asterix.service.ProcessingService;
import com.asterix.service.StatsBroadcaster;
import com.asterix.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final ProcessingService processing;
    private final DirectoryScanService scanner;
    private final StatsBroadcaster broadcaster;
    private final StatsService stats;

    public ApiController(ProcessingService processing, DirectoryScanService scanner,
                         StatsBroadcaster broadcaster, StatsService stats) {
        this.processing = processing;
        this.scanner = scanner;
        this.broadcaster = broadcaster;
        this.stats = stats;
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody Dtos.FolderRequest req) {
        try {
            processing.start(req.path());
            return ResponseEntity.ok(Map.of("running", true, "folder", processing.folder()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        processing.stop();
        return Map.of("running", false);
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        stats.reset();
        return Map.of("ok", true);
    }

    @GetMapping("/status")
    public Dtos.Stats status() {
        return broadcaster.buildStats();
    }

    @GetMapping("/flights")
    public List<Dtos.Flight> flights() {
        return broadcaster.buildFlights();
    }

    /** Server-side folder browser used by the "choose folder" UI. */
    @GetMapping("/fs")
    public ResponseEntity<?> browse(@RequestParam(required = false) String path) {
        try {
            return ResponseEntity.ok(scanner.browse(path));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

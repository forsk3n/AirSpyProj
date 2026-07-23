package com.asterix.service;

import com.asterix.repo.ProcessedFileRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/** Finds bitstream files to process and supports server-side folder browsing. */
@Service
public class DirectoryScanService {

    private final ProcessedFileRepository processed;

    // Files with these extensions are treated as bitstreams. Empty => any file.
    private final Set<String> extensions = Set.of(".sig", ".ast", ".bin", ".dat", ".raw");

    public DirectoryScanService(ProcessedFileRepository processed) {
        this.processed = processed;
    }

    public record Candidate(Path path, long size, Instant created) {}

    /** New, unprocessed files in {@code folder}, oldest creation time first. */
    public List<Candidate> pending(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) return List.of();
        List<Candidate> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(folder)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (!Files.isRegularFile(p)) continue;
                if (!matches(p)) continue;
                if (processed.existsByPath(p.toAbsolutePath().toString())) continue;
                BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
                out.add(new Candidate(p, a.size(), a.creationTime().toInstant()));
            }
        }
        out.sort(Comparator.comparing(Candidate::created)); // oldest -> newest
        return out;
    }

    private boolean matches(Path p) {
        if (extensions.isEmpty()) return true;
        String n = p.getFileName().toString().toLowerCase();
        return extensions.stream().anyMatch(n::endsWith);
    }

    // ---- folder browser (used by the web UI to pick a directory) ----
    public record Entry(String name, String path, boolean directory) {}

    public Map<String, Object> browse(String pathStr) throws IOException {
        Path dir = (pathStr == null || pathStr.isBlank())
                ? Paths.get(System.getProperty("user.home"))
                : Paths.get(pathStr);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current", dir.toAbsolutePath().toString());
        result.put("parent", dir.getParent() != null ? dir.getParent().toString() : null);
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isDirectory)
             .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
             .forEach(p -> entries.add(new Entry(p.getFileName().toString(),
                     p.toAbsolutePath().toString(), true)));
        }
        result.put("entries", entries);
        return result;
    }
}

package com.asterix.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Bookkeeping of files already consumed, so a folder can be re-scanned safely. */
@Entity
@Table(name = "processed_file")
public class ProcessedFile {

    @Id
    @Column(length = 512)
    private String path;             // absolute path (natural key)

    private long sizeBytes;
    private Instant fileCreated;     // source creation time (drives ordering)
    private Instant processedAt;
    private long blocks;
    private long errors;

    public ProcessedFile() {}

    public ProcessedFile(String path, long sizeBytes, Instant fileCreated) {
        this.path = path;
        this.sizeBytes = sizeBytes;
        this.fileCreated = fileCreated;
    }

    public String getPath() { return path; }
    public void setPath(String v) { this.path = v; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long v) { this.sizeBytes = v; }
    public Instant getFileCreated() { return fileCreated; }
    public void setFileCreated(Instant v) { this.fileCreated = v; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant v) { this.processedAt = v; }
    public long getBlocks() { return blocks; }
    public void setBlocks(long v) { this.blocks = v; }
    public long getErrors() { return errors; }
    public void setErrors(long v) { this.errors = v; }
}

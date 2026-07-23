package com.asterix.repo;

import com.asterix.model.ProcessedFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedFileRepository extends JpaRepository<ProcessedFile, String> {
    boolean existsByPath(String path);
}

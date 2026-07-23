package com.asterix.repo;

import com.asterix.model.FlightRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FlightRecordRepository extends JpaRepository<FlightRecord, Long> {

    Optional<FlightRecord> findFirstByTrackKeyAndState(String trackKey, FlightRecord.State state);

    List<FlightRecord> findTop500ByOrderByLastUpdatedDesc();

    long countByState(FlightRecord.State state);

    @Modifying
    @Query("update FlightRecord f set f.state = com.asterix.model.FlightRecord$State.INACTIVE "
         + "where f.state = com.asterix.model.FlightRecord$State.ACTIVE and f.lastUpdated < :cutoff")
    int markInactiveBefore(@Param("cutoff") Instant cutoff);
}

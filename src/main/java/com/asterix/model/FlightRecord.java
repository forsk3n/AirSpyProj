package com.asterix.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Aggregated state of one aircraft (one track), upserted as plots arrive.
 * Columns follow the agreed schema; enrichment columns (country / aircraft
 * type / model) are left null for now and filled later from a separate DB.
 */
@Entity
@Table(name = "flight_record",
       indexes = { @Index(name = "idx_trackkey", columnList = "trackKey"),
                   @Index(name = "idx_icao", columnList = "icaoAddress"),
                   @Index(name = "idx_state", columnList = "state") })
public class FlightRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 32)
    private String trackKey;         // stable identity (ICAO / track# / Mode 3-A)

    @Enumerated(EnumType.STRING)
    private SourceType sourceType;   // how the target was detected

    private Integer trackNumber;     // I048/161, when the sensor provides one

    @Column(length = 6)
    private String icaoAddress;      // ICAO 24-bit address (null for non-Mode-S)

    private String mode3A;           // Mode 3/A code (squawk)
    private String callsign;

    private String country;          // enrichment: country of registration (TODO)
    private String aircraftType;     // enrichment: type (TODO)
    private String aircraftModel;    // enrichment: model (TODO)

    private Double latitude;
    private Double longitude;
    private Double flightLevel;      // last known FL

    private Double startTod;         // first seen, seconds-of-day UTC
    private Double endTod;           // last seen, seconds-of-day UTC

    @Enumerated(EnumType.STRING)
    private State state = State.ACTIVE;

    private long plotCount;

    private Instant lastUpdated;     // wall-clock, drives inactivity timeout

    public enum State { ACTIVE, INACTIVE }
    public enum SourceType { MODE_S, MODE_AC, PRIMARY }

    // --- getters / setters ---
    public Long getId() { return id; }
    public String getTrackKey() { return trackKey; }
    public void setTrackKey(String v) { this.trackKey = v; }
    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType v) { this.sourceType = v; }
    public Integer getTrackNumber() { return trackNumber; }
    public void setTrackNumber(Integer v) { this.trackNumber = v; }
    public String getIcaoAddress() { return icaoAddress; }
    public void setIcaoAddress(String v) { this.icaoAddress = v; }
    public String getMode3A() { return mode3A; }
    public void setMode3A(String v) { this.mode3A = v; }
    public String getCallsign() { return callsign; }
    public void setCallsign(String v) { this.callsign = v; }
    public String getCountry() { return country; }
    public void setCountry(String v) { this.country = v; }
    public String getAircraftType() { return aircraftType; }
    public void setAircraftType(String v) { this.aircraftType = v; }
    public String getAircraftModel() { return aircraftModel; }
    public void setAircraftModel(String v) { this.aircraftModel = v; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double v) { this.latitude = v; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double v) { this.longitude = v; }
    public Double getFlightLevel() { return flightLevel; }
    public void setFlightLevel(Double v) { this.flightLevel = v; }
    public Double getStartTod() { return startTod; }
    public void setStartTod(Double v) { this.startTod = v; }
    public Double getEndTod() { return endTod; }
    public void setEndTod(Double v) { this.endTod = v; }
    public State getState() { return state; }
    public void setState(State v) { this.state = v; }
    public long getPlotCount() { return plotCount; }
    public void setPlotCount(long v) { this.plotCount = v; }
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant v) { this.lastUpdated = v; }
}

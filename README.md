# ASTERIX Processor

Real-time decoder for ASTERIX surveillance bitstreams (CAT048 target reports,
plus CAT034/008 service data) with a live web dashboard. Watches a folder,
processes files oldest-first, decodes ICAO address / Mode 3-A / callsign /
flight level / position, converts polar position to lat-lon using the sensor
database, stores one row per aircraft, and streams statistics to the browser
over WebSocket.

## Run

Requirements: JDK 21, Maven 3.9+.

```bash
mvn spring-boot:run
```

Open http://localhost:8080

1. Type or **Обзор…** (browse) to a folder that receives bitstream files
   (`.sig .ast .bin .dat .raw`), then press **Старт**.
2. The dashboard updates once per second: counters, CAT breakdown, per-sensor
   SAC/SIC table, the aircraft map, and the flights table.

The database is embedded H2 at `./data/asterix.mv.db` (browse it at
`/h2-console`, JDBC URL `jdbc:h2:file:./data/asterix`). To switch to
PostgreSQL, see the commented block in `application.yml`.

## How the bitstream is decoded

The recording wraps each ASTERIX data block in a 2-byte little-endian length:

```
file  = [ uint16LE length | block(length bytes) ]*
block = CAT(1) | LEN(2 BE) | FSPEC | data items…
```

`AsterixDecoder` walks the FSPEC against the CAT048 User Application Profile and
resolves each item's length (fixed / extendable-FX / repetitive / compound /
explicit) so the parser never desyncs. Extracted per plot: I048/010 (SAC/SIC),
/140 (time of day), /040 (polar position), /070 (Mode 3-A), /090 (flight level),
/220 (ICAO address), /240 (callsign).

`GeoUtil` turns the polar measurement into lat-lon: range (1/256 NM) is
slant-range-corrected with the aircraft altitude and the sensor elevation from
`RadarRegistry` (loaded from `radars.json`, keyed by SAC/SIC), then projected
along the azimuth on a spherical earth.

## Data model

`flight_record` — one row per target, upserted as plots arrive:
track key, source type, track number, ICAO address, Mode 3-A, callsign,
country / aircraft type / model (enrichment, currently null — see
`EnrichmentService`), latitude, longitude, flight level, start/end time of day,
plot count, state (ACTIVE/INACTIVE), last update.

Not every target carries an ICAO address. Targets are aggregated by a stable
identity chosen in this order: ICAO (Mode-S), else the sensor-scoped track
number (I048/161), else the sensor-scoped Mode 3-A code. This yields three
source types: MODE_S (Mode-S with ICAO), MODE_AC (secondary Mode A/C, no ICAO)
and PRIMARY (position only). Pure primary blips with no ICAO, track number or
Mode 3-A cannot be identified across scans; they are decoded and counted in the
statistics but are not written as aircraft rows.

A track goes INACTIVE after `asterix.inactivity-timeout-seconds` with no plot.

`processed_file` — bookkeeping so a folder can be re-scanned without
re-processing files already consumed.

## Layout

```
decoder/  AsterixDecoder, Cat048Plot, DecodeResult   — pure decode, no I/O
geo/      RadarRegistry, Radar, GeoUtil               — sensor db + projection
model/    FlightRecord, ProcessedFile                 — JPA entities
repo/     Spring Data repositories
service/  ProcessingService  — folder poll + orchestration
          TrackManager       — plot -> flight upsert (batched)
          StatsService       — cumulative counters
          StatsBroadcaster   — 1 Hz snapshot -> /topic/stats, /topic/flights
          DirectoryScanService, EnrichmentService
web/      ApiController (REST), Dtos
config/   WebSocketConfig
static/   index.html, app.js  — dashboard
```

## Notes / next steps

- Enrichment: wire `EnrichmentService` to resolve country from the ICAO 24-bit
  allocation ranges and type/model from an aircraft registration dataset.
- Position accuracy: current projection uses a spherical earth and a simple
  slant-range correction — adequate for display; swap in a WGS-84 geodesic and
  stereographic projection if you need survey-grade accuracy.
- Track identity: tracks are keyed by ICAO address. To separate distinct flights
  of the same airframe across a long recording, key by ICAO + a gap heuristic.
- Folder picking is server-side (the browser cannot hand a real path to the
  server): `/api/fs` lists directories on the machine running the app.

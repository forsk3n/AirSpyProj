package com.asterix.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads sensor positions (SAC/SIC -> lat/lon/elevation) from radars.json.
 * Invalid rows (out-of-range coordinates) are skipped and reported.
 */
@Component
public class RadarRegistry {

    private static final Logger log = LoggerFactory.getLogger(RadarRegistry.class);
    private final Map<Integer, Radar> byKey = new HashMap<>();
    private int skipped = 0;

    @PostConstruct
    void load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (var in = new ClassPathResource("radars.json").getInputStream()) {
            JsonNode root = mapper.readTree(in);
            for (JsonNode n : root) {
                int sac = n.path("SAC").asInt();
                int sic = n.path("SIC").asInt();
                Radar radar = new Radar(sac, sic,
                        n.path("Lat").asDouble(),
                        n.path("Long").asDouble(),
                        n.path("Elv").asDouble());
                if (radar.isValid()) {
                    byKey.put(key(sac, sic), radar);
                } else {
                    skipped++;
                }
            }
        }
        log.info("Radar registry: {} valid sensors, {} invalid rows skipped", byKey.size(), skipped);
    }

    public Radar find(int sac, int sic) {
        return byKey.get(key(sac, sic));
    }

    public int size() { return byKey.size(); }
    public int skipped() { return skipped; }

    private static int key(int sac, int sic) {
        return ((sac & 0xFF) << 8) | (sic & 0xFF);
    }
}

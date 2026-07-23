package com.asterix.service;

import com.asterix.model.FlightRecord;
import org.springframework.stereotype.Service;

/**
 * Fills the enrichment columns (country of registration, aircraft type, model).
 *
 * Left intentionally empty per the current spec: these come from a separate
 * database that is not wired in yet. The single hook below is the place to plug
 * it in — e.g. resolve country from the ICAO 24-bit address allocation ranges,
 * and type/model from an aircraft registration dataset keyed by ICAO address.
 */
@Service
public class EnrichmentService {

    public void enrich(FlightRecord f) {
        // TODO: country = IcaoCountry.resolve(f.getIcaoAddress());
        // TODO: var ac = aircraftDb.lookup(f.getIcaoAddress());
        //       f.setAircraftType(ac.type()); f.setAircraftModel(ac.model());
        // For now these stay null so the columns exist but are unpopulated.
    }
}

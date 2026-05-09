package com.skowronski.sda.telemetry.rest;

import com.skowronski.sda.telemetry.domain.Rso;
import com.skowronski.sda.telemetry.service.RsoCatalog;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/api/rso")
public class RsoController {

    private final RsoCatalog catalog;

    public RsoController(RsoCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public Collection<Rso> list() {
        return catalog.findAll();
    }

    @GetMapping("/{rsoId}")
    public ResponseEntity<Rso> get(@PathVariable String rsoId) {
        return catalog.findById(rsoId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Rso> create(@RequestBody Rso rso) {
        Rso saved = catalog.upsert(rso);
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{rsoId}")
    public Rso update(@PathVariable String rsoId, @RequestBody Rso rso) {
        Rso normalized = new Rso(
                rsoId,
                rso.designator(),
                rso.type(),
                rso.orbitClass(),
                rso.latitudeDeg(),
                rso.longitudeDeg(),
                rso.altitudeKm(),
                rso.lastUpdated()
        );
        return catalog.upsert(normalized);
    }

    @DeleteMapping("/{rsoId}")
    public ResponseEntity<Void> delete(@PathVariable String rsoId) {
        return catalog.delete(rsoId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}

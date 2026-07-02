package com.example.prospera.Resources;

import com.example.prospera.DTO.BudgetProgressRecord;
import com.example.prospera.DTO.BudgetRecord;
import com.example.prospera.Entities.Budget;
import com.example.prospera.Services.BudgetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/budgets")
public class BudgetResource {
    private final BudgetService service;

    public BudgetResource(BudgetService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<BudgetRecord>> findAll(@RequestParam(required = false) Integer month,
                                                      @RequestParam(required = false) Integer year) {
        List<BudgetRecord> budgets = service.findAuthenticatedUserBudgets(month, year).stream()
                .map(BudgetRecord::new)
                .toList();
        return ResponseEntity.ok(budgets);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BudgetRecord> findById(@PathVariable Integer id) {
        return ResponseEntity.ok(new BudgetRecord(service.findAuthenticatedUserBudget(id)));
    }

    @PostMapping
    public ResponseEntity<BudgetRecord> create(@RequestBody BudgetRecord record) {
        Budget budget = service.create(record);
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(budget.getId())
                .toUri();
        return ResponseEntity.created(uri).body(new BudgetRecord(budget));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BudgetRecord> update(@PathVariable Integer id, @RequestBody BudgetRecord record) {
        return ResponseEntity.ok(new BudgetRecord(service.update(id, record)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/progress")
    public ResponseEntity<List<BudgetProgressRecord>> progress(@RequestParam Integer month,
                                                               @RequestParam Integer year) {
        return ResponseEntity.ok(service.getProgress(month, year));
    }
}

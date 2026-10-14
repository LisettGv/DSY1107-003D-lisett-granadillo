package com.example.rabbitmq_cluster_dockcomp.controller;

import com.example.rabbitmq_cluster_dockcomp.service.ListenerManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/listeners")
public class ListenerController {

    private final ListenerManagementService listenerService;

    public ListenerController(ListenerManagementService listenerService) {
        this.listenerService = listenerService;
    }

    @GetMapping
    public ResponseEntity<List<String>> getAllListeners() {
        return ResponseEntity.ok(listenerService.getAllListenerIds());
    }

    @PostMapping("/{listenerId}/pause")
    public ResponseEntity<String> pauseListener(@PathVariable String listenerId) {
        listenerService.pauseListener(listenerId);
        return ResponseEntity.ok("Listener pausado: " + listenerId);
    }

    @PostMapping("/{listenerId}/resume")
    public ResponseEntity<String> resumeListener(@PathVariable String listenerId) {
        listenerService.resumeListener(listenerId);
        return ResponseEntity.ok("Listener reanudado: " + listenerId);
    }
}
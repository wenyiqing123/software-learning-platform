package edu.wfust.learning.platform.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {
    private final SystemStatusService service;

    public SystemStatusController(SystemStatusService service) { this.service = service; }

    @GetMapping("/status")
    public SystemStatusService.Status status() { return service.current(); }
}

package edu.wfust.learning.platform.practice;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/adaptive-practice")
public class AdaptivePracticeController {
    private final AdaptivePracticeService service;
    public AdaptivePracticeController(AdaptivePracticeService service){this.service=service;}
    @GetMapping public List<AdaptivePracticeService.Item> recommend(Authentication auth,@RequestParam(required=false) Long courseId,@RequestParam(defaultValue="10") int limit){return service.recommend(Long.parseLong(auth.getName()),courseId,limit);}
}

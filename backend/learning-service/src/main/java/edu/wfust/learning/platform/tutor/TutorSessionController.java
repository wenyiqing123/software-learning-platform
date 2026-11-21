package edu.wfust.learning.platform.tutor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/tutor/sessions") @PreAuthorize("hasRole('STUDENT')")
public class TutorSessionController {
    private final TutorSessionService sessions;
    public TutorSessionController(TutorSessionService sessions){this.sessions=sessions;}
    public record CreateSession(@Size(max=120) String title){}
    @GetMapping public List<TutorSessionService.SessionView> list(Authentication auth){return sessions.list(id(auth));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public TutorSessionService.SessionView create(Authentication auth,@Valid @RequestBody CreateSession body){return sessions.create(id(auth),body.title());}
    @GetMapping("/{sessionId}/messages") public List<TutorSessionService.MessageView> messages(Authentication auth,@PathVariable long sessionId){return sessions.messages(id(auth),sessionId);}
    @PostMapping("/{sessionId}/messages") @ResponseStatus(HttpStatus.CREATED) public TutorSessionService.MessageView append(Authentication auth,@PathVariable long sessionId,@Valid @RequestBody TutorSessionService.MessageInput body){return sessions.append(id(auth),sessionId,body);}
    @DeleteMapping("/{sessionId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(Authentication auth,@PathVariable long sessionId){sessions.delete(id(auth),sessionId);}
    private long id(Authentication auth){return Long.parseLong(auth.getName());}
}

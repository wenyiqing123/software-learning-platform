package edu.wfust.learning.platform.notes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/learning")
public class LearningNotesController {
    private final LearningNotesService service;
    public LearningNotesController(LearningNotesService service){this.service=service;}
    @GetMapping("/notes") public List<LearningNotesService.NoteView> notes(Authentication auth,@RequestParam(required=false) Long sectionId,@RequestParam(required=false) Long knowledgePointId){return service.list(owner(auth),sectionId,knowledgePointId);}
    @PostMapping("/notes") @ResponseStatus(HttpStatus.CREATED) public LearningNotesService.NoteView create(Authentication auth,@Valid @RequestBody NotePayload request){return service.create(owner(auth),request.toRequest());}
    @PutMapping("/notes/{id}") public LearningNotesService.NoteView update(Authentication auth,@PathVariable Long id,@Valid @RequestBody NotePayload request){return service.update(owner(auth),id,request.toRequest());}
    @DeleteMapping("/notes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(Authentication auth,@PathVariable Long id){service.delete(owner(auth),id);}
    @GetMapping("/bookmarks") public LearningNotesService.BookmarkView bookmark(Authentication auth,@RequestParam(required=false) Long sectionId,@RequestParam(required=false) Long knowledgePointId){return service.bookmark(owner(auth),sectionId,knowledgePointId);}
    @PutMapping("/bookmarks") public LearningNotesService.BookmarkView setBookmark(Authentication auth,@Valid @RequestBody BookmarkPayload request){return service.setBookmark(owner(auth),new LearningNotesService.BookmarkRequest(request.sectionId(),request.knowledgePointId(),request.bookmarked()));}
    private long owner(Authentication auth){return Long.parseLong(auth.getName());}
    public record NotePayload(Long sectionId,Long knowledgePointId,@Size(max=160) String title,@NotBlank @Size(max=10000) String content,@Size(max=10) List<@Size(max=24) String> tags){LearningNotesService.NoteRequest toRequest(){return new LearningNotesService.NoteRequest(sectionId,knowledgePointId,title,content,tags);}}
    public record BookmarkPayload(Long sectionId,Long knowledgePointId,boolean bookmarked){ }
}

package edu.wfust.learning.platform.notes;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@PreAuthorize("isAuthenticated()")
public class LearningNotesService {
    private final LearningNotesMapper mapper;
    private final ObjectMapper json;
    public LearningNotesService(LearningNotesMapper mapper, ObjectMapper json){this.mapper=mapper;this.json=json;}

    public List<NoteView> list(long userId, Long sectionId, Long knowledgePointId) {
        if (sectionId != null) requireSection(sectionId);
        if (knowledgePointId != null) requirePoint(knowledgePointId);
        return mapper.list(userId, sectionId, knowledgePointId).stream().map(this::view).toList();
    }

    @Transactional
    public NoteView create(long userId, NoteRequest request) { return save(userId, null, request); }

    @Transactional
    public NoteView update(long userId, long id, NoteRequest request) { owned(userId,id); return save(userId,id,request); }

    @Transactional
    public void delete(long userId, long id) { owned(userId,id); mapper.delete(id,userId); }

    public BookmarkView bookmark(long userId, Long sectionId, Long knowledgePointId) {
        Target target=target(sectionId,knowledgePointId);
        boolean marked=target.sectionId()!=null ? mapper.sectionBookmarked(userId,target.sectionId())>0 : mapper.pointBookmarked(userId,target.knowledgePointId())>0;
        return new BookmarkView(target.type(),target.id(),marked);
    }

    @Transactional
    public BookmarkView setBookmark(long userId, BookmarkRequest request) {
        Target target=target(request.sectionId(),request.knowledgePointId());
        if (target.sectionId()!=null) {
            if (request.bookmarked()) { if (mapper.sectionBookmarked(userId,target.sectionId())==0) mapper.addSectionBookmark(userId,target.sectionId()); }
            else mapper.removeSectionBookmark(userId,target.sectionId());
        } else {
            if (request.bookmarked()) { if (mapper.pointBookmarked(userId,target.knowledgePointId())==0) mapper.addPointBookmark(userId,target.knowledgePointId()); }
            else mapper.removePointBookmark(userId,target.knowledgePointId());
        }
        return new BookmarkView(target.type(),target.id(),request.bookmarked());
    }

    private NoteView save(long userId, Long id, NoteRequest request) {
        Target target=target(request.sectionId(),request.knowledgePointId());
        String title=request.title()==null?"":request.title().trim();
        String content=request.content()==null?"":request.content().trim();
        if (content.isBlank()) throw bad("笔记内容不能为空");
        List<String> tags=normalizeTags(request.tags());
        LearningNotesMapper.NoteRow row=new LearningNotesMapper.NoteRow(); row.setId(id);row.setUserId(userId);row.setSectionId(target.sectionId());row.setKnowledgePointId(target.knowledgePointId());row.setTitle(title);row.setContent(content);row.setTagsJson(write(tags));
        if(id==null) mapper.insert(row); else mapper.update(row);
        return view(id==null?mapper.owned(row.getId(),userId):mapper.owned(id,userId));
    }
    private LearningNotesMapper.NoteRow owned(long userId,long id){LearningNotesMapper.NoteRow row=mapper.owned(id,userId);if(row==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"笔记不存在或不属于当前用户");return row;}
    private void requireSection(Long id){if(id==null||mapper.sectionExists(id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"学习小节不存在");}
    private void requirePoint(Long id){if(id==null||mapper.pointExists(id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"知识点不存在或已停用");}
    private Target target(Long sectionId,Long pointId){if((sectionId==null)==(pointId==null))throw bad("笔记或收藏必须指定一个学习目标");if(sectionId!=null){requireSection(sectionId);return new Target("SECTION",sectionId,null);}requirePoint(pointId);return new Target("KNOWLEDGE_POINT",pointId,pointId);}
    private List<String> normalizeTags(List<String> tags){if(tags==null)return List.of();return new LinkedHashSet<>(tags.stream().filter(tag->tag!=null&&!tag.isBlank()).map(String::trim).filter(tag->!tag.isBlank()).limit(10).toList()).stream().toList();}
    private String write(List<String> value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("无法保存笔记标签",e);}}
    private List<String> read(String value){try{return json.readValue(value==null?"[]":value,new TypeReference<>(){});}catch(Exception e){return List.of();}}
    private NoteView view(LearningNotesMapper.NoteRow row){return new NoteView(row.getId(),row.getSectionId(),row.getKnowledgePointId(),row.getTitle(),row.getContent(),read(row.getTagsJson()),row.getCreatedAt(),row.getUpdatedAt());}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    public List<BookmarkItemView> listBookmarks(long userId) {
        var combined = new java.util.ArrayList<LearningNotesMapper.BookmarkRow>();
        var sections = mapper.listSectionBookmarks(userId);
        if (sections != null) combined.addAll(sections);
        var points = mapper.listPointBookmarks(userId);
        if (points != null) combined.addAll(points);
        return combined.stream()
                .sorted(java.util.Comparator.comparing(
                        LearningNotesMapper.BookmarkRow::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(row -> new BookmarkItemView(
                        row.getTargetType(), row.getTargetId(), row.getTitle(),
                        row.getCourseId(), row.getCourseName(), row.getKnowledgePointId(),
                        row.getKnowledgePointName(), row.getGraphEntity(), row.getCreatedAt()))
                .toList();
    }

    private record Target(String type,Long id,Long sectionId,Long knowledgePointId){Target(String type,Long id,Long pointId){this(type,id,type.equals("SECTION")?id:null,type.equals("SECTION")?null:pointId);}}
    public record NoteRequest(Long sectionId,Long knowledgePointId,String title,String content,List<String> tags) { }
    public record BookmarkRequest(Long sectionId,Long knowledgePointId,boolean bookmarked) { }
    public record NoteView(Long id,Long sectionId,Long knowledgePointId,String title,String content,List<String> tags,java.time.LocalDateTime createdAt,java.time.LocalDateTime updatedAt) { }
    public record BookmarkView(String targetType,Long targetId,boolean bookmarked) { }
    public record BookmarkItemView(String targetType,Long targetId,String title,Long courseId,String courseName,Long knowledgePointId,String knowledgePointName,String graphEntity,java.time.LocalDateTime createdAt) { }
}

package edu.wfust.learning.platform.notes;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LearningNotesServiceTest {
    private final LearningNotesMapper mapper=mock(LearningNotesMapper.class);
    private final LearningNotesService service=new LearningNotesService(mapper,JsonMapper.builder().build());

    @Test void createsOwnedSectionNoteWithNormalizedTags(){
        when(mapper.sectionExists(101L)).thenReturn(1);
        var row=new LearningNotesMapper.NoteRow();row.setId(7L);row.setUserId(9L);row.setSectionId(101L);row.setTitle("关键点");row.setContent("记录验收条件");row.setTagsJson("[\"需求\",\"验收\"]");
        doAnswer(invocation -> { ((LearningNotesMapper.NoteRow) invocation.getArgument(0)).setId(7L); return 1; }).when(mapper).insert(any());
        when(mapper.owned(7L,9L)).thenReturn(row);
        var result=service.create(9L,new LearningNotesService.NoteRequest(101L,null," 关键点 "," 记录验收条件 ",List.of("需求","需求","验收")));
        verify(mapper).insert(argThat(value->value.getUserId().equals(9L)&&value.getSectionId().equals(101L)&&value.getTagsJson().contains("验收")));
        assertThat(result.id()).isEqualTo(7L);assertThat(result.tags()).containsExactly("需求","验收");
    }

    @Test void refusesNoteWithoutValidTarget(){
        org.assertj.core.api.Assertions.assertThatThrownBy(()->service.create(9L,new LearningNotesService.NoteRequest(null,null,"","内容",List.of())))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("指定一个学习目标");
        verifyNoInteractions(mapper);
    }

    @Test void bookmarkIsIdempotentAndUserScoped(){
        when(mapper.sectionExists(101L)).thenReturn(1);when(mapper.sectionBookmarked(9L,101L)).thenReturn(0);
        var result=service.setBookmark(9L,new LearningNotesService.BookmarkRequest(101L,null,true));
        verify(mapper).addSectionBookmark(9L,101L);assertThat(result.bookmarked()).isTrue();
    }

    @Test void listBookmarksMergesAndSortsSectionAndPointBookmarks(){
        var olderPoint=new LearningNotesMapper.BookmarkRow();
        olderPoint.setTargetType("KNOWLEDGE_POINT");olderPoint.setTargetId(12L);olderPoint.setTitle("需求分析");
        olderPoint.setCourseId(1L);olderPoint.setCourseName("软件工程");olderPoint.setKnowledgePointId(12L);
        olderPoint.setKnowledgePointName("需求分析");olderPoint.setGraphEntity("需求分析");
        olderPoint.setCreatedAt(java.time.LocalDateTime.of(2026,11,26,9,0));

        var newerSection=new LearningNotesMapper.BookmarkRow();
        newerSection.setTargetType("SECTION");newerSection.setTargetId(34L);newerSection.setTitle("2.1 需求获取");
        newerSection.setCourseId(1L);newerSection.setCourseName("软件工程");newerSection.setKnowledgePointId(12L);
        newerSection.setKnowledgePointName("需求分析");newerSection.setGraphEntity("需求分析");
        newerSection.setCreatedAt(java.time.LocalDateTime.of(2026,11,26,10,30));

        when(mapper.listSectionBookmarks(9L)).thenReturn(List.of(newerSection));
        when(mapper.listPointBookmarks(9L)).thenReturn(List.of(olderPoint));

        var list=service.listBookmarks(9L);
        assertThat(list).hasSize(2);
        assertThat(list).extracting(LearningNotesService.BookmarkItemView::targetType).containsExactly("SECTION","KNOWLEDGE_POINT");
        assertThat(list.get(0).title()).isEqualTo("2.1 需求获取");
    }
}

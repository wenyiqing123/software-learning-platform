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
}

package com.codepath.academy.service;

import com.codepath.academy.entity.Course;
import com.codepath.academy.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseCatalogPaginationTest {
    @Mock CourseRepository courses;
    @Mock com.codepath.academy.repository.CategoryRepository categories;
    @Mock com.codepath.academy.repository.CourseSectionRepository sections;
    @InjectMocks CourseService service;

    @Test
    void filtersAndCountsAreAppliedBeforeMappingTheRequestedPage() {
        Course course = new Course();
        course.setId(20L);
        course.setCategoryId(2L);
        course.setTitle("Java");
        var pageable = PageRequest.of(1, 12, Sort.by("id").descending());
        when(courses.searchCatalog(false, 2L, "%java!_!%%", pageable))
                .thenReturn(new PageImpl<>(List.of(course), pageable, 25));
        var result = service.catalogPage("  JAVA_%  ", 2L, false, 1, 12);
        assertEquals(1, result.get("page"));
        assertEquals(12, result.get("size"));
        assertEquals(25L, result.get("totalElements"));
        assertEquals(3, result.get("totalPages"));
        var content = (List<?>) result.get("content");
        assertEquals(1, content.size());
        assertEquals(20L, ((Map<?, ?>) content.get(0)).get("id"));
        verify(courses).searchCatalog(false, 2L, "%java!_!%%", pageable);
        verify(courses, never()).findAll(any(Sort.class));
    }

    @Test
    void adminPagesIncludeUnpublishedCourses() {
        var pageable = PageRequest.of(0, 12, Sort.by("id").descending());
        when(courses.searchCatalog(true, null, "%%", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        var result = service.catalogPage(null, null, true, 0, 12);
        assertEquals(0L, result.get("totalElements"));
        assertEquals(0, result.get("totalPages"));
        assertEquals(List.of(), result.get("content"));
    }

    @Test
    void invalidPageOrSizeNeverQueriesDatabase() {
        assertThrows(IllegalArgumentException.class, () -> service.catalogPage("", null, false, -1, 12));
        assertThrows(IllegalArgumentException.class, () -> service.catalogPage("", null, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.catalogPage("", null, false, 0, 49));
        verifyNoInteractions(courses);
    }
}

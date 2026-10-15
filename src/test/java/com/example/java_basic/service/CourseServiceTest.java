package com.example.java_basic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.java_basic.entity.*;
import com.example.java_basic.enums.Role;
import com.example.java_basic.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

  @Mock
  CourseRepository courses;

  @Mock
  CategoryRepository categories;

  @Mock
  CourseSectionRepository sections;

  @Mock
  LessonRepository lessons;

  @Mock
  EnrollmentRepository enrollments;

  @Mock
  LessonProgressRepository progress;

  @Mock
  UserRepository users;

  @InjectMocks
  CourseService service;

  Course course;
  Lesson lesson;
  CourseSection section;
  User student;

  @BeforeEach
  void setup() {
    course = new Course();
    course.setId(1L);
    course.setPublished(true);
    section = new CourseSection();
    section.setId(2L);
    section.setCourseId(1L);
    lesson = new Lesson();
    lesson.setId(3L);
    lesson.setSectionId(2L);
    lesson.setContent("private lesson");
    lesson.setVideoUrl("https://example.com/private.mp4");
    student = User.builder().id(7L).role(Role.STUDENT).build();
    when(lessons.findById(3L)).thenReturn(Optional.of(lesson));
    when(sections.findById(2L)).thenReturn(Optional.of(section));
  }

  void stubCourse() {
    when(courses.findById(1L)).thenReturn(Optional.of(course));
  }

  @Test
  void privateLessonDeniedToAnonymous() {
    stubCourse();
    assertThrows(AccessDeniedException.class, () -> service.lesson(3L, null));
  }

  @Test
  void previewLessonPublicOnlyWhenPublished() {
    stubCourse();
    lesson.setPreview(true);
    assertEquals("private lesson", service.lesson(3L, null).get("content"));
    course.setPublished(false);
    assertThrows(AccessDeniedException.class, () -> service.lesson(3L, null));
  }

  @Test
  void enrolledStudentCanReadEvenWhenCourseHidden() {
    stubCourse();
    course.setPublished(false);
    when(enrollments.existsByUserIdAndCourseId(7L, 1L)).thenReturn(true);
    assertEquals(
      "https://example.com/private.mp4",
      service.lesson(3L, student).get("videoUrl")
    );
  }

  @Test
  void previewDoesNotPermitSavingProgressWithoutEnrollment() {
    assertThrows(AccessDeniedException.class, () ->
      service.updateProgress(3L, student, true)
    );
    verifyNoInteractions(progress);
  }

  @Test
  void progressUpdatedInPlace() {
    when(enrollments.existsByUserIdAndCourseId(7L, 1L)).thenReturn(true);
    when(users.lockById(7L)).thenReturn(Optional.of(student));
    LessonProgress old = new LessonProgress();
    old.setId(20L);
    when(progress.findByUserIdAndLessonId(7L, 3L)).thenReturn(Optional.of(old));
    service.updateProgress(3L, student, true);
    verify(progress).save(old);
    assertTrue(old.isCompleted());
    assertEquals(20L, old.getId());
  }
}

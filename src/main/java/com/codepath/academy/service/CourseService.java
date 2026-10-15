package com.codepath.academy.service;

import com.codepath.academy.dto.AcademyRequests.*;
import com.codepath.academy.entity.*;
import com.codepath.academy.repository.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courses;
    private final CategoryRepository categories;
    private final CourseSectionRepository sections;
    private final LessonRepository lessons;
    private final EnrollmentRepository enrollments;
    private final LessonProgressRepository progress;

    public static Map<String, Object> view(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
            result.put(
                    (String) pairs[i],
                    pairs[i + 1]
            );
        return result;
    }

    public static ResponseStatusException missing() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy dữ liệu"
        );
    }

    public boolean admin(User user) {
        return (
                user != null && user.getRole() == com.codepath.academy.enums.Role.ADMIN
        );
    }

    public Course requireCourse(Long id) {
        return courses.findById(id).orElseThrow(CourseService::missing);
    }

    public boolean enrolled(User user, Long id) {
        return (
                user != null && enrollments.existsByUserIdAndCourseId(user.getId(), id)
        );
    }

    public List<Category> categories() {
        return categories.findAll(org.springframework.data.domain.Sort.by("name"));
    }

    public List<Map<String, Object>> catalog(
            String query,
            Long categoryId,
            boolean all
    ) {
        String q = Objects.toString(query, "").strip().toLowerCase(Locale.ROOT);
        return courses
                .findAll(org.springframework.data.domain.Sort.by("id").descending())
                .stream()
                .filter(c -> all || c.isPublished())
                .filter(c -> categoryId == null || categoryId.equals(c.getCategoryId()))
                .filter(
                        c ->
                                c.getTitle().toLowerCase(Locale.ROOT).contains(q) ||
                                        c.getDescription().toLowerCase(Locale.ROOT).contains(q)
                )
                .map(this::card)
                .toList();
    }

    public Map<String, Object> card(Course c) {
        List<Lesson> ls = courseLessons(c.getId());
        return view(
                "id",
                c.getId(),
                "categoryId",
                c.getCategoryId(),
                "categoryName",
                categories.findById(c.getCategoryId()).map(Category::getName).orElse(""),
                "title",
                c.getTitle(),
                "description",
                c.getDescription(),
                "price",
                c.getPrice(),
                "thumbnailUrl",
                c.getThumbnailUrl(),
                "instructor",
                c.getInstructor(),
                "level",
                c.getLevel(),
                "published",
                c.isPublished(),
                "lessonCount",
                ls.size(),
                "durationMinutes",
                ls.stream().mapToInt(Lesson::getDurationMinutes).sum()
        );
    }

    public Map<String, Object> catalogPage(String query, Long categoryId, boolean all, int page, int size) {
        if (page < 0 || size < 1 || size > 48)
            throw new IllegalArgumentException("Trang phải từ 0; số khóa học mỗi trang từ 1 đến 48.");
        String q = Objects.toString(query, "").strip().toLowerCase(Locale.ROOT);
        String pattern = "%" + q.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
        var result = courses.searchCatalog(all, categoryId, pattern,
                PageRequest.of(page, size, Sort.by("id").descending()));
        return view("content", result.getContent().stream().map(this::card).toList(),
                "page", result.getNumber(), "size", result.getSize(),
                "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages());
    }

    public List<Lesson> courseLessons(Long courseId) {
        return sections
                .findByCourseIdOrderByPositionAscIdAsc(courseId)
                .stream()
                .flatMap(s ->
                        lessons.findBySectionIdOrderByPositionAscIdAsc(s.getId()).stream()
                )
                .toList();
    }

    public Map<String, Object> detail(Long id, User user) {
        Course c = requireCourse(id);
        boolean owns = enrolled(user, id);
        boolean admin = admin(user);
        if (!c.isPublished() && !owns && !admin) throw missing();
        Map<String, Object> result = card(c);
        result.put("enrolled", owns);
        result.put("resumeLessonId", resumeLessonId(user, courseLessons(id)));
        Set<Long> done = new HashSet<>();
        if (user != null) progress
                .findByUserId(user.getId())
                .stream()
                .filter(LessonProgress::isCompleted)
                .forEach(p -> done.add(p.getLessonId()));
        result.put(
                "sections",
                sections
                        .findByCourseIdOrderByPositionAscIdAsc(id)
                        .stream()
                        .map(s ->
                                view(
                                        "id",
                                        s.getId(),
                                        "title",
                                        s.getTitle(),
                                        "position",
                                        s.getPosition(),
                                        "lessons",
                                        lessons
                                                .findBySectionIdOrderByPositionAscIdAsc(s.getId())
                                                .stream()
                                                .map(l ->
                                                        view(
                                                                "id",
                                                                l.getId(),
                                                                "title",
                                                                l.getTitle(),
                                                                "position",
                                                                l.getPosition(),
                                                                "preview",
                                                                l.isPreview(),
                                                                "durationMinutes",
                                                                l.getDurationMinutes(),
                                                                "completed",
                                                                done.contains(l.getId())
                                                        )
                                                )
                                                .toList()
                                )
                        )
                        .toList()
        );
        return result;
    }

    public Map<String, Object> lesson(Long id, User user) {
        Lesson l = lessons.findById(id).orElseThrow(CourseService::missing);
        CourseSection s = sections
                .findById(l.getSectionId())
                .orElseThrow(CourseService::missing);
        Course c = requireCourse(s.getCourseId());
        if (
                !admin(user) &&
                        !enrolled(user, c.getId()) &&
                        !(c.isPublished() && l.isPreview())
        ) throw new AccessDeniedException("Chưa đăng ký khóa học");
        LessonProgress saved = user == null ? null : progress.findByUserIdAndLessonId(user.getId(), id).orElse(null);
        return view(
                "id",
                l.getId(),
                "courseId",
                c.getId(),
                "sectionId",
                s.getId(),
                "title",
                l.getTitle(),
                "content",
                l.getContent(),
                "videoUrl",
                l.getVideoUrl(),
                "muxStatus",
                l.getMuxStatus(),
                "positionSeconds", saved == null ? 0L : saved.getPositionSeconds(),
                "videoDurationSeconds", saved == null ? 0L : saved.getDurationSeconds(),
                "positionUpdatedAt", saved == null ? 0L : saved.getPositionUpdatedAt(),
                "preview",
                l.isPreview(),
                "durationMinutes",
                l.getDurationMinutes(),
                "position",
                l.getPosition()
        );
    }

    public List<Map<String, Object>> library(User user) {
        Set<Long> done = new HashSet<>();
        progress
                .findByUserId(user.getId())
                .stream()
                .filter(LessonProgress::isCompleted)
                .forEach(p -> done.add(p.getLessonId()));
        return enrollments
                .findByUserIdOrderByIdDesc(user.getId())
                .stream()
                .map(e -> {
                    Course c = requireCourse(e.getCourseId());
                    Map<String, Object> v = card(c);
                    List<Lesson> ls = courseLessons(c.getId());
                    v.put(
                            "completedLessons",
                            ls
                                    .stream()
                                    .filter(l -> done.contains(l.getId()))
                                    .count()
                    );
                    v.put("enrolledAt", e.getEnrolledAt());
                    return v;
                })
                .toList();
    }

    @Transactional
    public void updateProgress(Long lessonId, User user, boolean completed) {
        updateProgress(lessonId, user, new ProgressInput(completed, null, null));
    }

    @Transactional
    public Map<String, Object> updateProgress(Long lessonId, User user, ProgressInput input) {
        Lesson l = lessons.findById(lessonId).orElseThrow(CourseService::missing);
        Long courseId = sections
                .findById(l.getSectionId())
                .orElseThrow(CourseService::missing)
                .getCourseId();
        if (!enrolled(user, courseId)) throw new AccessDeniedException(
                "Chưa đăng ký khóa học"
        );
        if (input.completed() == null && input.positionSeconds() == null)
            throw new IllegalArgumentException("Cần trạng thái hoàn thành hoặc vị trí xem.");
        if (input.positionSeconds() != null && (input.durationSeconds() == null
                || input.positionSeconds() < 0 || input.durationSeconds() < 1
                || input.durationSeconds() > 604800 || input.positionSeconds() > input.durationSeconds()))
            throw new IllegalArgumentException("Vị trí xem hoặc thời lượng video không hợp lệ.");
        // Serialize updates for the same student, including simultaneous first writes.
        usersLock(user.getId());
        LessonProgress p = progress
                .findByUserIdAndLessonId(user.getId(), lessonId)
                .orElseGet(LessonProgress::new);
        p.setUserId(user.getId());
        p.setLessonId(lessonId);
        if (input.completed() != null) p.setCompleted(input.completed());
        if (input.positionSeconds() != null) {
            p.setPositionSeconds(input.positionSeconds());
            p.setDurationSeconds(input.durationSeconds());
            p.setPositionUpdatedAt(System.currentTimeMillis());
            if (input.positionSeconds() >= input.durationSeconds() * 0.95) {
                p.setCompleted(true);
                p.setPositionSeconds(0);
            }
        }
        if (Boolean.TRUE.equals(input.completed())) {
            p.setPositionSeconds(0);
            p.setPositionUpdatedAt(System.currentTimeMillis());
        }
        p.setUpdatedAt(LocalDateTime.now());
        progress.save(p);
        return view("message", "Đã lưu tiến độ", "completed", p.isCompleted(),
                "positionSeconds", p.getPositionSeconds(), "durationSeconds", p.getDurationSeconds(),
                "positionUpdatedAt", p.getPositionUpdatedAt());
    }

    private Long resumeLessonId(User user, List<Lesson> courseLessons) {
        if (user == null) return null;
        Set<Long> ids = new HashSet<>();
        courseLessons.forEach(l -> ids.add(l.getId()));
        return progress.findByUserId(user.getId()).stream()
                .filter(p -> ids.contains(p.getLessonId()) && p.getDurationSeconds() > 0)
                .max(Comparator.comparingLong(LessonProgress::getPositionUpdatedAt))
                .map(LessonProgress::getLessonId).orElse(null);
    }

    private final UserRepository users;

    private void usersLock(Long id) {
        users.lockById(id).orElseThrow(CourseService::missing);
    }

    @Transactional
    public Category saveCategory(Long id, CategoryInput input) {
        Category c =
                id == null
                        ? new Category()
                        : categories.findById(id).orElseThrow(CourseService::missing);
        c.setName(input.name().strip());
        return categories.save(c);
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (courses.existsByCategoryId(id)) throw new IllegalStateException(
                "Danh mục đang chứa khóa học"
        );
        categories.delete(
                categories.findById(id).orElseThrow(CourseService::missing)
        );
    }

    @Transactional
    public Map<String, Object> saveCourse(Long id, CourseInput in, User admin) {
        categories.findById(in.categoryId()).orElseThrow(CourseService::missing);
        validateUrl(in.thumbnailUrl());
        Course c = id == null ? new Course() : requireCourse(id);
        c.setCategoryId(in.categoryId());
        c.setTitle(in.title().strip());
        c.setDescription(in.description());
        c.setPrice(in.price());
        c.setThumbnailUrl(in.thumbnailUrl());
        c.setInstructor(in.instructor().strip());
        c.setLevel(in.level());
        c.setPublished(in.published());
        courses.save(c);
        return detail(c.getId(), admin);
    }

    @Transactional
    public CourseSection saveSection(Long courseId, Long id, SectionInput in) {
        requireCourse(courseId);
        CourseSection s =
                id == null
                        ? new CourseSection()
                        : sections.findById(id).orElseThrow(CourseService::missing);
        if (id != null && !s.getCourseId().equals(courseId)) throw missing();
        s.setCourseId(courseId);
        s.setTitle(in.title().strip());
        s.setPosition(in.position());
        return sections.save(s);
    }

    @Transactional
    public Lesson saveLesson(Long sectionId, Long id, LessonInput in) {
        sections.findById(sectionId).orElseThrow(CourseService::missing);
        validateUrl(in.videoUrl());
        Lesson l =
                id == null
                        ? new Lesson()
                        : lessons.lockById(id).orElseThrow(CourseService::missing);
        if (id != null && !l.getSectionId().equals(sectionId)) throw missing();
        l.setSectionId(sectionId);
        l.setTitle(in.title().strip());
        l.setContent(in.content());
        l.setVideoUrl(in.videoUrl());
        l.setDurationMinutes(in.durationMinutes());
        l.setPosition(in.position());
        l.setPreview(in.preview());
        return lessons.save(l);
    }

    @Transactional
    public void deleteLesson(Long id) {
        Lesson lesson = lessons.lockById(id).orElseThrow(CourseService::missing);
        requireNoMuxVideo(lesson);
        lessons.delete(lesson);
    }

    @Transactional
    public void deleteSection(Long id) {
        CourseSection s = sections.findById(id).orElseThrow(CourseService::missing);
        var chapterLessons = lessons.findBySectionIdOrderByPositionAscIdAsc(id);
        chapterLessons.forEach(this::requireNoMuxVideo);
        lessons.deleteAll(chapterLessons);
        sections.delete(s);
    }

    private void requireNoMuxVideo(Lesson lesson) {
        if (lesson.getMuxUploadId() != null) throw new IllegalStateException(
                "Hãy xóa video Mux trong trình sửa bài học trước khi xóa bài học hoặc chương.");
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            URI u = URI.create(url);
            if (
                    !List.of("https", "http").contains(u.getScheme()) ||
                            u.getHost() == null ||
                            u.getUserInfo() != null
            ) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Đường dẫn phải là URL HTTP hoặc HTTPS hợp lệ"
            );
        }
    }
}

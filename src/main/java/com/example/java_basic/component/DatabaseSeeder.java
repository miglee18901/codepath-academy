package com.example.java_basic.component;

import com.example.java_basic.entity.*;
import com.example.java_basic.enums.Role;
import com.example.java_basic.repository.*;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
public class DatabaseSeeder implements CommandLineRunner {

  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final CategoryRepository categories;
  private final CourseRepository courses;
  private final CourseSectionRepository sections;
  private final LessonRepository lessons;

  @Override
  @Transactional
  public void run(String... args) {
    user("admin", "123456", Role.ADMIN, "Quản trị viên");
    user("student", "Learn123!", Role.STUDENT, "Học viên trải nghiệm");
    if (courses.count() != 0) return;
    String[] names = { "Java & Backend", "Frontend", "Cơ sở lập trình" };
    var cats = new java.util.ArrayList<Category>();
    for (String name : names) {
      Category c = new Category();
      c.setName(name);
      cats.add(categories.save(c));
    }
    create(
      cats.get(0).getId(),
      "Java Backend từ nền tảng đến REST API",
      new BigDecimal("1290000"),
      "Java 17, Spring Boot, thiết kế REST API và xây dựng ứng dụng backend có kiểm thử.",
      "Trung cấp",
      new String[] {
        "Java và tư duy hướng đối tượng",
        "Spring Boot và REST API",
        "Database và bảo mật",
      }
    );
    create(
      cats.get(1).getId(),
      "React: xây dựng giao diện ứng dụng",
      new BigDecimal("990000"),
      "Làm chủ component, hooks và quản lý trạng thái để xây dựng giao diện React hiện đại.",
      "Cơ bản",
      new String[] {
        "Component và JSX",
        "Hooks và dữ liệu",
        "Kết nối với backend",
      }
    );
    create(
      cats.get(2).getId(),
      "Nhập môn tư duy lập trình",
      BigDecimal.ZERO,
      "Bắt đầu từ biến, điều kiện và vòng lặp. Khóa học miễn phí để trải nghiệm nền tảng học tập.",
      "Nhập môn",
      new String[] { "Bắt đầu hành trình", "Giải quyết vấn đề" }
    );
  }

  private void user(String username, String password, Role role, String name) {
    if (!users.existsByUsername(username)) users.save(
      User.builder()
        .username(username)
        .password(passwords.encode(password))
        .fullName(name)
        .email(username + "@codepath.local")
        .role(role)
        .build()
    );
  }

  private void create(
    Long categoryId,
    String title,
    BigDecimal price,
    String description,
    String level,
    String[] chapters
  ) {
    Course c = new Course();
    c.setCategoryId(categoryId);
    c.setTitle(title);
    c.setPrice(price);
    c.setDescription(description);
    c.setInstructor("Đội ngũ CodePath");
    c.setLevel(level);
    c.setPublished(true);
    courses.save(c);
    for (int i = 0; i < chapters.length; i++) {
      CourseSection s = new CourseSection();
      s.setCourseId(c.getId());
      s.setTitle(chapters[i]);
      s.setPosition(i);
      sections.save(s);
      for (int j = 0; j < 2; j++) {
        Lesson l = new Lesson();
        l.setSectionId(s.getId());
        l.setTitle(j == 0 ? "Khái niệm và ví dụ" : "Thực hành từng bước");
        l.setPosition(j);
        l.setDurationMinutes(15 + j * 5);
        l.setPreview(i == 0 && j == 0);
        l.setContent(
          "NỘI DUNG MINH HỌA\n\n" +
            chapters[i] +
            "\n\nMục tiêu: hiểu khái niệm và áp dụng bằng một ví dụ nhỏ.\n\n1. Đọc khái niệm và xác định đầu vào, đầu ra.\n2. Viết lời giải theo từng bước.\n3. Chạy thử với trường hợp thông thường và trường hợp biên.\n\nBài tập: tự viết một ví dụ và giải thích vì sao nó hoạt động.\n\nĐây là bài học mẫu để kiểm tra hệ thống. Quản trị viên cần thay bằng nội dung khóa học thực tế."
        );
        lessons.save(l);
      }
    }
  }
}

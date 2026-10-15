CREATE TABLE users (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(20) NOT NULL UNIQUE, email VARCHAR(100) NOT NULL UNIQUE,
 full_name VARCHAR(50) NOT NULL, password VARCHAR(255) NOT NULL, role VARCHAR(20) NOT NULL
);
CREATE TABLE categories (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE);
CREATE TABLE courses (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, category_id BIGINT NOT NULL,
 title VARCHAR(160) NOT NULL, description TEXT NOT NULL, price DECIMAL(12,0) NOT NULL,
 thumbnail_url VARCHAR(1000), instructor VARCHAR(100) NOT NULL, level VARCHAR(30) NOT NULL, published BIT NOT NULL,
 CONSTRAINT fk_course_category FOREIGN KEY(category_id) REFERENCES categories(id),
 CONSTRAINT ck_course_price CHECK(price>=0)
);
CREATE TABLE course_sections (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, course_id BIGINT NOT NULL, title VARCHAR(160) NOT NULL, position INT NOT NULL,
 CONSTRAINT fk_section_course FOREIGN KEY(course_id) REFERENCES courses(id), INDEX idx_section_order(course_id,position)
);
CREATE TABLE lessons (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, section_id BIGINT NOT NULL, title VARCHAR(160) NOT NULL,
 content LONGTEXT NOT NULL, video_url VARCHAR(1000), duration_minutes INT NOT NULL, position INT NOT NULL, preview BIT NOT NULL,
 CONSTRAINT fk_lesson_section FOREIGN KEY(section_id) REFERENCES course_sections(id), INDEX idx_lesson_order(section_id,position)
);
CREATE TABLE orders (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, total DECIMAL(12,0) NOT NULL,
 status VARCHAR(20) NOT NULL, checkout_url VARCHAR(1000), created_at DATETIME(6) NOT NULL, paid_at DATETIME(6),
 CONSTRAINT fk_order_user FOREIGN KEY(user_id) REFERENCES users(id), INDEX idx_user_orders(user_id,id),
 CONSTRAINT ck_order_total CHECK(total>=0)
);
CREATE TABLE order_items (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, course_id BIGINT NOT NULL,
 course_title VARCHAR(160) NOT NULL, price DECIMAL(12,0) NOT NULL,
 CONSTRAINT fk_item_order FOREIGN KEY(order_id) REFERENCES orders(id),
 CONSTRAINT fk_item_course FOREIGN KEY(course_id) REFERENCES courses(id), UNIQUE KEY uq_order_course(order_id,course_id)
);
CREATE TABLE payments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL UNIQUE,
 external_ref VARCHAR(100) NOT NULL UNIQUE, amount DECIMAL(12,0) NOT NULL, paid_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_payment_order FOREIGN KEY(order_id) REFERENCES orders(id)
);
CREATE TABLE enrollments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, course_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL, enrolled_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_enrollment_user FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT fk_enrollment_course FOREIGN KEY(course_id) REFERENCES courses(id),
 CONSTRAINT fk_enrollment_order FOREIGN KEY(order_id) REFERENCES orders(id), UNIQUE KEY uq_enrollment(user_id,course_id)
);
CREATE TABLE lesson_progress (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, lesson_id BIGINT NOT NULL,
 completed BIT NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_progress_user FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT fk_progress_lesson FOREIGN KEY(lesson_id) REFERENCES lessons(id) ON DELETE CASCADE,
 UNIQUE KEY uq_progress(user_id,lesson_id)
);

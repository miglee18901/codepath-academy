package com.codepath.academy.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lessons")
@Getter
@Setter
@NoArgsConstructor
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sectionId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(length = 1000)
    private String videoUrl;

    @Column(length = 100)
    private String muxUploadId;

    @Column(length = 100)
    private String muxAssetId;

    @Column(length = 100)
    private String muxPlaybackId;

    @Column(length = 30)
    private String muxStatus;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean preview;
}

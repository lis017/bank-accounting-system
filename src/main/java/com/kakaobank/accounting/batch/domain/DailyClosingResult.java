package com.kakaobank.accounting.batch.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일마감 배치 결과를 보관하는 엔티티.
 *
 * 운영 관점에서 "오늘 마감이 정상 끝났는지"를 한 번에 확인하기 위해 결과 테이블을 별도로 둡니다.
 * Spring Batch의 BATCH_JOB_EXECUTION에도 정보가 남지만, 비즈니스 지표(처리 건수 등)는
 * 별도 도메인 테이블에 두는 편이 회계팀/감사팀이 사용하기 편합니다.
 */
@Entity
@Table(name = "daily_closing_result",
        indexes = @Index(name = "idx_daily_closing_target_date", columnList = "target_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class DailyClosingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    @Column(name = "success_count", nullable = false)
    private Integer successCount;

    @Column(name = "failure_count", nullable = false)
    private Integer failureCount;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @PrePersist
    void onCreate() {
        this.executedAt = LocalDateTime.now();
    }

    public static DailyClosingResult of(LocalDate targetDate, int total, int success, int failure) {
        return DailyClosingResult.builder()
                .targetDate(targetDate)
                .totalCount(total)
                .successCount(success)
                .failureCount(failure)
                .build();
    }
}

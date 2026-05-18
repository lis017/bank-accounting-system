package com.kakaobank.accounting.budget.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 부서 엔티티.
 *
 * 금융회계에서 예산은 일반적으로 "어느 조직(부서)이 어느 계정(계정과목)에 얼마를 쓸 수 있는가"로 관리되므로,
 * 부서는 예산의 1차 분류 기준이 됩니다.
 *
 * departmentCode는 외부 시스템(인사/조직)과 매핑되는 비즈니스 식별자라고 가정해 unique 제약을 둡니다.
 */
@Entity
@Table(name = "department",
        uniqueConstraints = @UniqueConstraint(name = "uq_department_code", columnNames = "department_code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "department_code", nullable = false, length = 32)
    private String departmentCode;

    @Column(name = "department_name", nullable = false, length = 64)
    private String departmentName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static Department create(String departmentCode, String departmentName) {
        return Department.builder()
                .departmentCode(departmentCode)
                .departmentName(departmentName)
                .build();
    }
}

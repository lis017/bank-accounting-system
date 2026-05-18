package com.kakaobank.accounting.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 집행 실행 요청 바디.
 *
 * 멱등성 키와 함께 요청 내용을 해싱해 비교하기 위해 본 요청 객체의 필드를 사용합니다.
 * 빈 객체가 들어와도 멱등성은 동작해야 하므로 memo는 nullable 허용 + Size 제한만 둡니다.
 */
public record ExpenseRequestExecuteRequest(
        @NotBlank @Size(max = 200) String executionNote
) { }

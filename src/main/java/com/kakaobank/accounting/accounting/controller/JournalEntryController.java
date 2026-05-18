package com.kakaobank.accounting.accounting.controller;

import com.kakaobank.accounting.accounting.dto.JournalEntryResponse;
import com.kakaobank.accounting.accounting.service.JournalEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/journal-entries")
@RequiredArgsConstructor
public class JournalEntryController {

    private final JournalEntryService journalEntryService;

    /**
     * 특정 회계일자 기준 전표 목록 조회.
     *
     * 조회 조건이므로 RequestParam을 사용합니다.
     */
    @GetMapping
    public ResponseEntity<List<JournalEntryResponse>> findByTargetDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        List<JournalEntryResponse> body = journalEntryService.findByTargetDate(targetDate).stream()
                .map(JournalEntryResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }
}

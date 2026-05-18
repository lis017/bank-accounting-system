package com.kakaobank.accounting.batch.controller;

import com.kakaobank.accounting.batch.daily.DailyClosingJobConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 배치 트리거 API.
 *
 * 실무에서는 보통 스케줄러(예: Spring Scheduler, Airflow, Jenkins)가 호출하지만,
 * 본 과제에서는 데모와 테스트를 위해 HTTP로 트리거할 수 있게 합니다.
 *
 * 면접 포인트:
 *   - "왜 일마감을 API가 아니라 Batch로 처리하는가?"
 *     배치 메타테이블에 실행/재시작 이력이 남아 운영 추적이 가능하고,
 *     Chunk 기반 트랜잭션 분할로 부분 실패에도 안전합니다.
 *   - API는 단지 "지금 한번 돌려라"라는 트리거 역할에 한정합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
public class BatchController {

    private final JobLauncher jobLauncher;
    private final Job dailyClosingJob;

    @PostMapping("/daily-closing")
    public ResponseEntity<Void> runDailyClosing(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) throws Exception {
        JobParameters params = new JobParametersBuilder()
                // 같은 targetDate라도 재실행을 허용하기 위해 timestamp를 함께 둡니다.
                // Batch는 동일 JobParameters로는 한 번만 실행되기 때문입니다.
                .addString("targetDate", targetDate.toString())
                .addLong("triggeredAt", System.currentTimeMillis())
                .toJobParameters();
        JobExecution execution = jobLauncher.run(dailyClosingJob, params);
        log.info("BATCH_TRIGGERED jobName={} targetDate={} executionId={} status={}",
                DailyClosingJobConfig.JOB_NAME, targetDate, execution.getId(), execution.getStatus());
        return ResponseEntity.accepted().build();
    }
}

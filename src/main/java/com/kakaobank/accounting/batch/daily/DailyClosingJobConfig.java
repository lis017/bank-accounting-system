package com.kakaobank.accounting.batch.daily;

import com.kakaobank.accounting.accounting.repository.JournalEntryRepository;
import com.kakaobank.accounting.batch.domain.DailyClosingResult;
import com.kakaobank.accounting.batch.repository.DailyClosingResultRepository;
import com.kakaobank.accounting.expense.domain.ExpenseRequest;
import com.kakaobank.accounting.expense.domain.ExpenseRequestStatus;
import com.kakaobank.accounting.expense.repository.ExpenseRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * dailyClosingJob 설정.
 *
 * JobParameters:
 *   - targetDate: 마감 대상 회계일자 (yyyy-MM-dd)
 *
 * 흐름:
 *   Reader: targetDate + EXECUTED 상태인 ExpenseRequest를 페이지 단위로 읽음
 *   Processor: 연결된 회계전표의 차/대변 합계 검증, 카운터 업데이트
 *   Writer: 검증 통과 건을 CLOSED로 전이
 *   JobListener: 종료 시 DailyClosingResult 1건 저장
 *
 * 면접 포인트:
 *   - chunkSize는 100으로 두었습니다. 카뱅 규모처럼 트래픽이 큰 환경에서는
 *     "한 트랜잭션의 길이"와 "메모리 사용량"의 균형을 위해 100~1000 사이가 일반적입니다.
 *   - chunkSize를 너무 크게 하면 한 트랜잭션이 길어져 락 점유/롤백 비용이 커지고,
 *     너무 작게 하면 트랜잭션 오버헤드가 증가합니다.
 *   - RepositoryItemReader를 사용한 이유: JPA 기반이므로 도메인 메서드를 그대로 활용할 수 있고,
 *     테스트 작성이 쉽습니다. 대용량(수백만 건)에서는 JdbcPagingItemReader가 더 효율적이지만,
 *     본 과제 규모와 도메인 일관성 측면에서는 RepositoryItemReader가 더 적합하다고 판단했습니다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DailyClosingJobConfig {

    public static final String JOB_NAME = "dailyClosingJob";
    public static final String STEP_NAME = "dailyClosingStep";
    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ExpenseRequestRepository expenseRequestRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final DailyClosingResultRepository dailyClosingResultRepository;

    @Bean
    public Job dailyClosingJob(Step dailyClosingStep, JobExecutionListener dailyClosingListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(dailyClosingListener)
                .start(dailyClosingStep)
                .build();
    }

    @Bean
    public Step dailyClosingStep(RepositoryItemReader<ExpenseRequest> dailyClosingReader,
                                 DailyClosingProcessor dailyClosingProcessor,
                                 DailyClosingWriter dailyClosingWriter) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<ExpenseRequest, ExpenseRequest>chunk(CHUNK_SIZE, transactionManager)
                .reader(dailyClosingReader)
                .processor(dailyClosingProcessor)
                .writer(dailyClosingWriter)
                .build();
    }

    /**
     * targetDate + EXECUTED 상태인 집행 요청만 읽어옵니다.
     * Sort는 id 기준 오름차순. 동일 page 요청에서 결과가 흔들리지 않도록 반드시 명시적 sort가 필요합니다.
     */
    @Bean
    @StepScope
    public RepositoryItemReader<ExpenseRequest> dailyClosingReader(
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {
        LocalDate targetDate = LocalDate.parse(targetDateStr);
        return new RepositoryItemReaderBuilder<ExpenseRequest>()
                .name("dailyClosingReader")
                .repository(expenseRequestRepository)
                .methodName("findByTargetDateAndStatus")
                .arguments(List.of(targetDate, ExpenseRequestStatus.EXECUTED))
                .pageSize(CHUNK_SIZE)
                .sorts(Map.of("id", Sort.Direction.ASC))
                .build();
    }

    @Bean
    @StepScope
    public DailyClosingProcessor dailyClosingProcessor(DailyClosingCounter counter) {
        return new DailyClosingProcessor(journalEntryRepository, counter);
    }

    @Bean
    @StepScope
    public DailyClosingWriter dailyClosingWriter() {
        return new DailyClosingWriter(expenseRequestRepository);
    }

    /**
     * Job 종료 시 DailyClosingResult를 저장하는 리스너.
     *
     * Step 안에 두지 않고 Job 레벨로 둔 이유: Step이 여러 개로 늘어나도 결과 저장 위치를 한 곳에서 관리하기 위함.
     */
    @Bean
    @JobScope
    public JobExecutionListener dailyClosingListener(DailyClosingCounter counter,
                                                     @Value("#{jobParameters['targetDate']}") String targetDateStr) {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                log.info("DAILY_CLOSING_JOB_START targetDate={}", targetDateStr);
            }

            @Override
            public void afterJob(JobExecution jobExecution) {
                LocalDate targetDate = LocalDate.parse(targetDateStr);
                DailyClosingResult result = DailyClosingResult.of(
                        targetDate,
                        counter.getTotalCount(),
                        counter.getSuccessCount(),
                        counter.getFailureCount());
                dailyClosingResultRepository.save(result);
                log.info("DAILY_CLOSING_JOB_END targetDate={} total={} success={} failure={} status={}",
                        targetDateStr,
                        counter.getTotalCount(),
                        counter.getSuccessCount(),
                        counter.getFailureCount(),
                        jobExecution.getStatus());
            }
        };
    }
}

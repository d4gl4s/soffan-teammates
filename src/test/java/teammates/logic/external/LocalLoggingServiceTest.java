package teammates.logic.external;

import java.time.Instant;

import org.testng.annotations.Test;

import teammates.common.datatransfer.QueryLogsResults;
import teammates.common.datatransfer.logs.QueryLogsParams;
import teammates.test.BaseTestCase;

/**
 * SUT: {@link LocalLoggingService}.
 */
public class LocalLoggingServiceTest extends BaseTestCase {

    private final LocalLoggingService llogService = new LocalLoggingService();

    @Test
    public void testDefaultLogsContainStatus200() {
        long currentTimestamp = Instant.now().toEpochMilli();
        QueryLogsParams params = QueryLogsParams.builder(0, currentTimestamp)
                .withStatus("200")
                .build();
        QueryLogsResults res = llogService.queryLogs(params);

        assertFalse(res.getLogEntries().isEmpty());
    }

    @Test
    public void testDefaultLogsContainQueryLogsAction() {
        long currentTimestamp = Instant.now().toEpochMilli();
        QueryLogsParams params = QueryLogsParams.builder(0, currentTimestamp)
                .withActionClass("QueryLogsAction")
                .build();
        QueryLogsResults res = llogService.queryLogs(params);

        assertFalse(res.getLogEntries().isEmpty());
    }

    @Test
    public void testDefaultLogsContainAboveZeroLatency() {
        long currentTimestamp = Instant.now().toEpochMilli();
        QueryLogsParams params = QueryLogsParams.builder(0, currentTimestamp)
                .withLatency(">0")
                .build();
        QueryLogsResults res = llogService.queryLogs(params);

        assertFalse(res.getLogEntries().isEmpty());
    }

    @Test
    public void testDefaultLogsDoesNotContainNegativeLatency() {
        long currentTimestamp = Instant.now().toEpochMilli();
        QueryLogsParams params = QueryLogsParams.builder(0, currentTimestamp)
                .withLatency("<0")
                .build();
        QueryLogsResults res = llogService.queryLogs(params);

        assertTrue(res.getLogEntries().isEmpty());
    }
}

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
    public void testDefaultLogsHaveNextPage() {
        llogService.createFeedbackSessionLog("dd2480", "teacher@kth.se", "test", "test");
        long currentTimestamp = Instant.now().toEpochMilli();
        QueryLogsParams params = QueryLogsParams.builder(0, currentTimestamp)
                .withActionClass("")
                .build();
        QueryLogsResults res = llogService.queryLogs(params);

        assertTrue(res.getHasNextPage());
    }
}

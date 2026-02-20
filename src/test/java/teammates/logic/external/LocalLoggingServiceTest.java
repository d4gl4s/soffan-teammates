package teammates.logic.external;

import org.testng.annotations.AfterClass;

import teammates.test.BaseTestCase;

/**
 * SUT: {@link LocalLoggingService}.
 */
public class LocalLoggingServiceTest extends BaseTestCase {
    @AfterClass
    public void printDiyBranchCoverageReport() {
        LocalLoggingService.printManualCoverageReport();
    }
}

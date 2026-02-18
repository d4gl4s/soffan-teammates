package teammates.logic.external;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.math3.random.RandomDataGenerator;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonParseException;

import teammates.common.datatransfer.FeedbackSessionLogEntry;
import teammates.common.datatransfer.QueryLogsResults;
import teammates.common.datatransfer.logs.ExceptionLogDetails;
import teammates.common.datatransfer.logs.GeneralLogEntry;
import teammates.common.datatransfer.logs.LogDetails;
import teammates.common.datatransfer.logs.LogEvent;
import teammates.common.datatransfer.logs.QueryLogsParams;
import teammates.common.datatransfer.logs.RequestLogDetails;
import teammates.common.datatransfer.logs.RequestLogUser;
import teammates.common.util.FileHelper;
import teammates.common.util.JsonUtils;
import teammates.common.util.Logger;

/**
 * Holds functions for operations related to logs reading/writing in local dev environment.
 *
 * <p>The current implementation uses an in-memory storage of logs to simulate the logs
 * retention locally for feedback session logs only. It is not meant as a replacement but
 * merely for testing purposes.
 */
public class LocalLoggingService implements LogService {

    private static final Map<String, List<FeedbackSessionLogEntry>> FEEDBACK_SESSION_LOG_ENTRIES = new ConcurrentHashMap<>();
    private static final List<GeneralLogEntry> LOCAL_LOG_ENTRIES = loadLocalLogEntries();
    private static final String ASCENDING_ORDER = "asc";

    private static List<GeneralLogEntry> loadLocalLogEntries() {
        // Timestamp of logs are randomly created to be within the last one hour
        long currentTimestamp = Instant.now().toEpochMilli();
        long earliestTimestamp = currentTimestamp - 60 * 60 * 1000;
        try {
            String jsonString = FileHelper.readResourceFile("logsForLocalDev.json");
            Type type = new TypeToken<Collection<GeneralLogEntry>>(){}.getType();
            Collection<GeneralLogEntry> logEntriesCollection = JsonUtils.fromJson(jsonString, type);
            return logEntriesCollection.stream()
                    .map(log -> {
                        long timestamp = new RandomDataGenerator().nextLong(earliestTimestamp, currentTimestamp);
                        GeneralLogEntry logEntryWithUpdatedTimestamp = new GeneralLogEntry(
                                log.getSeverity(), log.getTrace(), log.getInsertId(), log.getResourceIdentifier(),
                                log.getSourceLocation(), timestamp);
                        logEntryWithUpdatedTimestamp.setDetails(log.getDetails());
                        logEntryWithUpdatedTimestamp.setMessage(log.getMessage());
                        return logEntryWithUpdatedTimestamp;
                    })
                    .collect(Collectors.toList());
        } catch (JsonParseException e) {
            return new ArrayList<>();
        }
    }

    @Override
    public QueryLogsResults queryLogs(QueryLogsParams queryLogsParams) {
        // Page size is set as a small value to test loading of more logs
        int pageSize = 10;

        List<GeneralLogEntry> result = LOCAL_LOG_ENTRIES.stream()
                .sorted((x, y) -> {
                    String order = queryLogsParams.getOrder();
                    if (ASCENDING_ORDER.equals(order)) {
                        return Long.compare(x.getTimestamp(), y.getTimestamp());
                    } else {
                        return Long.compare(y.getTimestamp(), x.getTimestamp());
                    }
                })
                .filter(log -> queryLogsParams.getSeverity() == null
                        || log.getSeverity() == queryLogsParams.getSeverity())
                .filter(log -> queryLogsParams.getMinSeverity() == null
                        || log.getSeverity().getSeverityLevel()
                            >= queryLogsParams.getMinSeverity().getSeverityLevel())
                .filter(log -> log.getTimestamp() > queryLogsParams.getStartTime())
                .filter(log -> log.getTimestamp() <= queryLogsParams.getEndTime())
                .filter(log -> queryLogsParams.getTraceId() == null
                        || queryLogsParams.getTraceId().equals(log.getTrace()))
                .filter(log -> queryLogsParams.getVersion() == null
                        || queryLogsParams.getVersion().equals(log.getResourceIdentifier().get("version_id")))
                .filter(log -> queryLogsParams.getSourceLocation().getFile() == null
                        || log.getSourceLocation().getFile().equals(queryLogsParams.getSourceLocation().getFile()))
                .filter(log -> queryLogsParams.getSourceLocation().getFunction() == null
                        || log.getSourceLocation().getFunction().equals(queryLogsParams.getSourceLocation().getFunction()))
                .filter(log -> isEventBasedFilterSatisfied(log, queryLogsParams))
                .limit(pageSize)
                .collect(Collectors.toList());

        List<GeneralLogEntry> copiedResults = deepCopyLogEntries(result);
        boolean hasNextPage = copiedResults.size() == pageSize;

        return new QueryLogsResults(copiedResults, hasNextPage);
    }

    private boolean isEventBasedFilterSatisfied(GeneralLogEntry log, QueryLogsParams queryLogsParams) {
        String actionClassFilter = queryLogsParams.getActionClass();
        String exceptionClassFilter = queryLogsParams.getExceptionClass();
        String logEventFilter = queryLogsParams.getLogEvent();
        String latencyFilter = queryLogsParams.getLatency();
        String statusFilter = queryLogsParams.getStatus();

        RequestLogUser userInfoFilter = queryLogsParams.getUserInfoParams();
        String regkeyFilter = userInfoFilter.getRegkey();
        String emailFilter = userInfoFilter.getEmail();
        String googleIdFilter = userInfoFilter.getGoogleId();

        if (actionClassFilter == null && exceptionClassFilter == null && logEventFilter == null
                && latencyFilter == null && statusFilter == null && regkeyFilter == null
                && emailFilter == null && googleIdFilter == null) {
            return true;
        }
        LogDetails details = log.getDetails();
        if (details == null) {
            return false;
        }
        if (logEventFilter != null && !details.getEvent().name().equals(logEventFilter)) {
            return false;
        }
        if (!isExceptionFilterSatisfied(details, exceptionClassFilter)) {
            return false;
        }
        return isRequestFilterSatisfied(details, actionClassFilter, latencyFilter, statusFilter,
                regkeyFilter, emailFilter, googleIdFilter);
    }

    private boolean isExceptionFilterSatisfied(LogDetails details, String exceptionClassFilter) {
        if (exceptionClassFilter == null) {
            return true;
        }
        if (details.getEvent() != LogEvent.EXCEPTION_LOG) {
            return false;
        }
        ExceptionLogDetails exceptionDetails = (ExceptionLogDetails) details;
        return exceptionDetails.getExceptionClass().equals(exceptionClassFilter);
    }

    private boolean isRequestFilterSatisfied(LogDetails details, String actionClassFilter,
            String latencyFilter, String statusFilter, String regkeyFilter, String emailFilter, String googleIdFilter) {
        if (actionClassFilter == null && latencyFilter == null && statusFilter == null
                && regkeyFilter == null && emailFilter == null && googleIdFilter == null) {
            ManualBranchCoverage.mark(1);
            return true;
        } else {
            ManualBranchCoverage.mark(2);
        }
        if (details.getEvent() != LogEvent.REQUEST_LOG) {
            ManualBranchCoverage.mark(3);
            return false;
        } else {
            ManualBranchCoverage.mark(4);
        }
        RequestLogDetails requestDetails = (RequestLogDetails) details;
        if (actionClassFilter != null && !actionClassFilter.equals(requestDetails.getActionClass())) {
            ManualBranchCoverage.mark(5);
            return false;
        } else {
            ManualBranchCoverage.mark(6);
        }
        if (statusFilter != null && !statusFilter.equals(String.valueOf(requestDetails.getResponseStatus()))) {
            ManualBranchCoverage.mark(7);
            return false;
        } else {
            ManualBranchCoverage.mark(8);
        }
        if (latencyFilter != null) {
            ManualBranchCoverage.mark(9);
            Pattern p = Pattern.compile("^(>|>=|<|<=) *(\\d+)$");
            Matcher m = p.matcher(latencyFilter);
            long logLatency = ((RequestLogDetails) details).getResponseTime();
            boolean isFilterSatisfied = false;
            if (m.matches()) {
                ManualBranchCoverage.mark(10);
                int time = Integer.parseInt(m.group(2));
                switch (m.group(1)) {
                case ">":
                    ManualBranchCoverage.mark(11);
                    isFilterSatisfied = logLatency > time;
                    break;
                case ">=":
                    ManualBranchCoverage.mark(12);
                    isFilterSatisfied = logLatency >= time;
                    break;
                case "<":
                    ManualBranchCoverage.mark(13);
                    isFilterSatisfied = logLatency < time;
                    break;
                case "<=":
                    ManualBranchCoverage.mark(14);
                    isFilterSatisfied = logLatency <= time;
                    break;
                default:
                    ManualBranchCoverage.mark(15);
                    assert false : "Unreachable case";
                    break;
                }
            }
            if (!isFilterSatisfied) {
                ManualBranchCoverage.mark(16);
                return false;
            } else {
                ManualBranchCoverage.mark(17);
            }
        } else {
            ManualBranchCoverage.mark(18);
        }
        RequestLogUser userInfo = requestDetails.getUserInfo();
        if (regkeyFilter != null && (userInfo == null || !regkeyFilter.equals(userInfo.getRegkey()))) {
            ManualBranchCoverage.mark(19);
            return false;
        } else {
            ManualBranchCoverage.mark(20);
        }
        if (emailFilter != null && (userInfo == null || !emailFilter.equals(userInfo.getEmail()))) {
            ManualBranchCoverage.mark(21);
            return false;
        } else {
            ManualBranchCoverage.mark(22);
        }
        return googleIdFilter == null || userInfo != null && googleIdFilter.equals(userInfo.getGoogleId());
    }

    @Override
    public void createFeedbackSessionLog(String courseId, String email, String fsName, String fslType) {
        FeedbackSessionLogEntry logEntry = new FeedbackSessionLogEntry(courseId, email,
                fsName, fslType, Instant.now().toEpochMilli());
        FEEDBACK_SESSION_LOG_ENTRIES.computeIfAbsent(courseId, k -> new ArrayList<>()).add(logEntry);
    }

    @Override
    public void createFeedbackSessionLog(String courseId, UUID studentId, UUID fsId, String fslType) {
        FeedbackSessionLogEntry logEntry = new FeedbackSessionLogEntry(courseId, studentId, fsId,
                fslType, Instant.now().toEpochMilli());
        FEEDBACK_SESSION_LOG_ENTRIES.computeIfAbsent(courseId, k -> new ArrayList<>()).add(logEntry);
    }

    @Override
    public List<FeedbackSessionLogEntry> getOrderedFeedbackSessionLogs(String courseId, String email,
            long startTime, long endTime, String fsName) {
        return FEEDBACK_SESSION_LOG_ENTRIES
                .getOrDefault(courseId, new ArrayList<>())
                .stream()
                .filter(log -> email == null || log.getStudentEmail().equals(email))
                .filter(log -> fsName == null || log.getFeedbackSessionName().equals(fsName))
                .filter(log -> log.getTimestamp() >= startTime)
                .filter(log -> log.getTimestamp() <= endTime)
                .sorted()
                .collect(Collectors.toList());
    }

    private List<GeneralLogEntry> deepCopyLogEntries(List<GeneralLogEntry> logEntries) {
        List<GeneralLogEntry> result = new ArrayList<>();
        for (GeneralLogEntry logEntry : logEntries) {
            GeneralLogEntry copiedEntry = new GeneralLogEntry(logEntry.getSeverity(),
                    logEntry.getTrace(), logEntry.getInsertId(), logEntry.getResourceIdentifier(),
                    logEntry.getSourceLocation(), logEntry.getTimestamp());
            copiedEntry.setDetails(JsonUtils.fromJson(JsonUtils.toCompactJson(logEntry.getDetails()), LogDetails.class));
            copiedEntry.setMessage(logEntry.getMessage());
            result.add(copiedEntry);
        }

        return result;
    }

    static void printManualCoverageReport() {
        ManualBranchCoverage.printSummary();
    }

    private static final class ManualBranchCoverage {
        private static final Logger log = Logger.getLogger();
        private static final int TOTAL_BRANCHES = 17;
        private static final boolean[] COVERED = new boolean[TOTAL_BRANCHES + 1];

        private static synchronized void mark(int branchId) {
            COVERED[branchId] = true;
        }

        private static synchronized void printSummary() {
            int coveredCount = 0;
            StringBuilder coveredBranchIds = new StringBuilder();
            for (int i = 1; i <= TOTAL_BRANCHES; i++) {
                if (!COVERED[i]) {
                    continue;
                }
                coveredCount++;
                if (coveredBranchIds.length() > 0) {
                    coveredBranchIds.append(", ");
                }
                coveredBranchIds.append(i);
            }

            double percentage = coveredCount * 100.0 / TOTAL_BRANCHES;
            double roundedPercentage = Math.round(percentage * 100.0) / 100.0;
            log.info("DIY coverage: "
                    + coveredCount + "/" + TOTAL_BRANCHES + " branches (" + roundedPercentage + "%). "
                    + "Covered IDs: [" + coveredBranchIds + "]");
        }
    }
}

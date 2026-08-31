package com.orbitworkbench.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orbitworkbench.interview.domain.InterviewTurnType;
import com.orbitworkbench.studyplan.domain.StudyTaskPriority;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RequestEnumsTest {

    @Test
    void parsesAndTrimsSubmittedValue() {
        assertEquals(InterviewTurnType.MAIN,
                RequestEnums.parse(InterviewTurnType.class, "  MAIN  ", "turnType"));
        assertEquals(StudyTaskPriority.HIGH,
                RequestEnums.parse(StudyTaskPriority.class, "HIGH", "priority"));
    }

    @Test
    void rejectsUnknownValueAsBadRequestNamingOnlyTheField() {
        ApiException exception = assertThrows(ApiException.class,
                () -> RequestEnums.parse(InterviewTurnType.class, "MAIN'; DROP TABLE", "turnType"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        assertEquals("turnType 取值不合法", exception.getMessage());
    }

    @Test
    void rejectsMissingAndWrongCaseValues() {
        assertThrows(ApiException.class,
                () -> RequestEnums.parse(InterviewTurnType.class, null, "turnType"));
        assertThrows(ApiException.class,
                () -> RequestEnums.parse(InterviewTurnType.class, "  ", "turnType"));
        assertThrows(ApiException.class,
                () -> RequestEnums.parse(InterviewTurnType.class, "main", "turnType"));
    }
}

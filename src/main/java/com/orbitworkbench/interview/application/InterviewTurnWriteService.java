package com.orbitworkbench.interview.application;

import com.orbitworkbench.interview.domain.InterviewSessionStatus;
import com.orbitworkbench.interview.domain.InterviewTurnRecord;
import com.orbitworkbench.interview.domain.InterviewTurnType;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewSessionMapper;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewTurnMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serialize question writes with session transitions, without holding a lock during AI calls. */
@Service
public class InterviewTurnWriteService {
    private final InterviewSessionMapper sessions;
    private final InterviewTurnMapper turns;

    public InterviewTurnWriteService(InterviewSessionMapper sessions, InterviewTurnMapper turns) {
        this.sessions = sessions;
        this.turns = turns;
    }

    @Transactional
    public InterviewTurnRecord append(Long userId, Long sessionId, InterviewTurnType type,
                                      String question, int expectedTurnCount) {
        var session = sessions.findByIdForUpdate(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "面试会话不存在");
        }
        if (session.getStatus() != InterviewSessionStatus.RUNNING) {
            throw conflict("会话已暂停或结束，本次生成的题目未保存");
        }
        int count = turns.countBySession(sessionId);
        int typeLimit = type == InterviewTurnType.MAIN
                ? session.getQuestionLimit() : session.getFollowUpLimit();
        if (count != expectedTurnCount || count >= session.getTurnLimit()
                || turns.countBySessionAndType(sessionId, type) >= typeLimit) {
            throw conflict("问答进度或题量已变化，请刷新后重试");
        }
        if (type == InterviewTurnType.FOLLOW_UP) {
            var history = turns.listBySession(sessionId);
            if (history.isEmpty() || history.getLast().getAnswer() == null) {
                throw conflict("追问必须基于最后一条已回答的问题");
            }
        }
        var turn = new InterviewTurnRecord();
        turn.setSessionId(sessionId);
        turn.setTurnNo(count + 1);
        turn.setTurnType(type);
        turn.setQuestion(question);
        turn.setCreatedAt(Instant.now());
        turns.insert(turn);
        return turn;
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, message);
    }
}

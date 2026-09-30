package com.economistwars.citizen;

import com.economistwars.network.CitizenProfilePayload;
import java.util.List;

/** A citizen goal that can be considered by the situation-aware decision planner. */
interface CitizenDecisionAction {
    /** Refreshes any values shared by score and detail reporting for one planner snapshot. */
    default void prepareDecisionSnapshot() {}
    CitizenDecisionPlanner.Action decisionAction();
    double decisionScore();
    int estimatedDurationTicks();
    List<CitizenProfilePayload.DecisionDetail> decisionDetails();
}

package com.economistwars.citizen;

import java.util.List;
import com.economistwars.network.CitizenProfilePayload;
import net.minecraft.core.BlockPos;

public record CitizenActionEvaluation(boolean eligible,double score,int duration,BlockPos destination,
        List<CitizenProfilePayload.DecisionDetail> details) {
    static CitizenActionEvaluation unavailable() { return new CitizenActionEvaluation(false,0,0,null,List.of()); }
    static CitizenActionEvaluation priority(boolean eligible,double score,int duration,BlockPos target,String rule) {
        return new CitizenActionEvaluation(eligible,score,Math.max(1,duration),target,List.of(
                new CitizenProfilePayload.DecisionDetail("Score rule",rule),
                new CitizenProfilePayload.DecisionDetail("Score",String.format(java.util.Locale.ROOT,"%.4f",score))));
    }
}

package com.fc.v2.model.custom;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 辅导风险判档入参（rule-eval 形状）
 *
 * <p>ruleCode 留空时按 at 时刻全体在任判据抢档（顺位大者先说话，并列取代号在后者）；
 * evaluatedAt 是评估做成之日，补做讨结论的活按那一日取版本，不按今日。</p>
 *
 * @author fuce
 * @date 2026-09-27
 */
public class RiskGradeQuery {

    /** 判据代号（离婚调适/家庭暴力倾向/未成年人受影响程度/财产纠纷激化 各管各的）；留空=跨代号抢档 */
    private String ruleCode;

    /** 辅导员所给分值 */
    private BigDecimal score;

    /** 评估做成之日（按此日请出在任版本） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date evaluatedAt;

    public RiskGradeQuery() {
    }

    public RiskGradeQuery(String ruleCode, BigDecimal score, Date evaluatedAt) {
        this.ruleCode = ruleCode;
        this.score = score;
        this.evaluatedAt = evaluatedAt;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public Date getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(Date evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }
}

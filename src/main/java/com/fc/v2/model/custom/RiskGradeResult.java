package com.fc.v2.model.custom;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fc.v2.model.auto.TMarrRiskLine;

/**
 * 辅导风险判档结论快照（rule-eval 形状）
 *
 * <p>结论一旦压上即定死：档位连同判据代号、该版启用之日一并封在本对象里，
 * 同一份评估今天算、明天算必须是同一个结果，页面只许原样取用，不得事后手工再盖。</p>
 *
 * <ul>
 *   <li>{@link #OK} 正常定档（level 取 1/2/3）</li>
 *   <li>{@link #NO_RULE} 该日无判据可依——直说，不许拿邻近日子的版本顶</li>
 *   <li>{@link #ILLEGAL_SCORE} 分值不成体统（负数、四位九等），退回辅导员重填，不硬算</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-27
 */
public class RiskGradeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 正常定档 */
    public static final String OK = "OK";
    /** 评估做成之日无在任判据可依 */
    public static final String NO_RULE = "NO_RULE";
    /** 分值不成体统（负数 / 四位九等越界） */
    public static final String ILLEGAL_SCORE = "ILLEGAL_SCORE";

    /** 结论事由码 */
    private String reasonCode;

    /** 结论事由（人话，供辅导员与处里同看一页） */
    private String reason;

    /** 档位 1一档 2二档 3三档；未定档为 null */
    private Integer level;

    /** 认下的判据代号；未定档为 null */
    private String ruleCode;

    /** 认下的那一版启用之日（压结论用）；未定档为 null */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date effStart;

    /** 交棒之日(不含)，随快照带出供核对 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date effEnd;

    /** 评估做成之日 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date evaluatedAt;

    /** 参与定档的分值 */
    private BigDecimal score;

    public static RiskGradeResult ok(int level, TMarrRiskLine rule, BigDecimal score, Date evaluatedAt) {
        RiskGradeResult r = new RiskGradeResult();
        r.reasonCode = OK;
        r.reason = "已定档";
        r.level = level;
        r.ruleCode = rule.getRuleCode();
        r.effStart = rule.getEffStart();
        r.effEnd = rule.getEffEnd();
        r.score = score;
        r.evaluatedAt = evaluatedAt;
        return r;
    }

    public static RiskGradeResult noRule(String ruleCode, BigDecimal score, Date evaluatedAt) {
        RiskGradeResult r = new RiskGradeResult();
        r.reasonCode = NO_RULE;
        r.ruleCode = ruleCode;
        r.score = score;
        r.evaluatedAt = evaluatedAt;
        if (ruleCode == null || ruleCode.trim().isEmpty()) {
            r.reason = "该日无在任判据可依";
        } else {
            r.reason = "判据[" + ruleCode + "]在该日无在任版本可依";
        }
        return r;
    }

    public static RiskGradeResult illegalScore(BigDecimal score, Date evaluatedAt) {
        RiskGradeResult r = new RiskGradeResult();
        r.reasonCode = ILLEGAL_SCORE;
        r.reason = "分值不成体统（须为 0..999 的数值），请退回辅导员重填";
        r.score = score;
        r.evaluatedAt = evaluatedAt;
        return r;
    }

    /** 是否真的压上了档位 */
    public boolean isGraded() {
        return OK.equals(reasonCode) && level != null;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public Date getEffEnd() {
        return effEnd;
    }

    public void setEffEnd(Date effEnd) {
        this.effEnd = effEnd;
    }

    public Date getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(Date evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }
}

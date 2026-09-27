package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMarrRiskLineMapper;
import com.fc.v2.model.auto.TMarrRiskLine;
import com.fc.v2.model.custom.RiskGradeQuery;
import com.fc.v2.model.custom.RiskGradeResult;
import com.fc.v2.service.impl.TMarrRiskLineServiceImpl;

/**
 * 辅导风险判据取用与算档口径单测（不启 Spring、不连库，Mapper 以内存清单顶）。
 *
 * @author fuce
 * @date 2026-09-27
 */
class TMarrRiskLineServiceImplTest {

    private TMarrRiskLineServiceImpl service;
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Date d(String s) throws Exception {
        return fmt.parse(s);
    }

    private TMarrRiskLine rule(long id, String code, String th1, String th2, String th3,
                               String start, String end, int priority, int status) {
        TMarrRiskLine r = new TMarrRiskLine();
        r.setId(id);
        r.setRuleCode(code);
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        try {
            r.setEffStart(start == null ? null : d(start));
            r.setEffEnd(end == null ? null : d(end));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        service = new TMarrRiskLineServiceImpl();
        TMarrRiskLineMapper mapper = mock(TMarrRiskLineMapper.class);
        ReflectionTestUtils.setField(service, "marrRiskLineMapper", mapper);

        final List<TMarrRiskLine> seed = new ArrayList<TMarrRiskLine>();
        // 离婚调适旧版：th3 缺格不设界；今年三月交棒
        seed.add(rule(10, "DIVORCE_ADJ", "10", "20", null,
                "2025-01-01 00:00:00", "2026-03-01 00:00:00", 10, 1));
        // 离婚调适新版：三月一日起站着
        seed.add(rule(11, "DIVORCE_ADJ", "8", "16", "24",
                "2026-03-01 00:00:00", null, 10, 0));
        // 家暴：顺位 20
        seed.add(rule(20, "VIOLENCE", "5", "12", "20",
                "2025-01-01 00:00:00", null, 20, 0));
        // 未成年人受影响：顺位 20（并列），代号在前
        seed.add(rule(30, "MINOR_IMPACT", "5", "12", "20",
                "2025-06-01 00:00:00", null, 20, 0));
        // 财产纠纷：作废条（停用且无交棒日）
        seed.add(rule(40, "PROPERTY", "6", "14", "22",
                "2025-01-01 00:00:00", null, 30, 1));

        // 贴真库行为：selectList 认 wrapper 里的 rule_code 等值过滤
        when(mapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<TMarrRiskLine> qw = (QueryWrapper<TMarrRiskLine>) inv.getArgument(0);
            String code = ruleCodeOf(qw);
            List<TMarrRiskLine> out = new ArrayList<TMarrRiskLine>();
            for (TMarrRiskLine r : seed) {
                if (code == null || code.equals(r.getRuleCode())) {
                    out.add(r);
                }
            }
            return out;
        });
        when(mapper.selectById(40L)).thenReturn(seed.get(4));
    }

    /** 从 QueryWrapper 里抠出 eq("rule_code", ?) 的绑定值；没有该条件返回 null */
    private static String ruleCodeOf(QueryWrapper<?> qw) {
        String seg = qw == null ? null : qw.getSqlSegment();
        if (seg == null || !seg.contains("rule_code")) {
            return null;
        }
        Matcher m = Pattern.compile("rule_code\\s*=\\s*#\\{[^}]*?(\\w+)\\}").matcher(seg);
        if (!m.find()) {
            return null;
        }
        Object v = qw.getParamNameValuePairs().get(m.group(1));
        return v == null ? null : v.toString();
    }

    // ===== 算档：挨着界算高档 =====

    @Test
    void level_belowFirstBound_isTier1() throws Exception {
        Date at = d("2026-02-15 10:00:00");
        assertEquals(1, service.evaluate("DIVORCE_ADJ", new BigDecimal("9"), at));
    }

    @Test
    void level_equalFirstBound_promotesToTier2() throws Exception {
        // 正与一档界一般高 -> 写二档
        Date at = d("2026-02-15 10:00:00");
        assertEquals(2, service.evaluate("DIVORCE_ADJ", new BigDecimal("10"), at));
    }

    @Test
    void level_equalSecondBound_promotesToTier3() throws Exception {
        // 最易错的一句：正与二档界一般高，写三档
        Date at = d("2026-02-15 10:00:00");
        assertEquals(3, service.evaluate("DIVORCE_ADJ", new BigDecimal("20"), at));
    }

    @Test
    void level_beyondThirdBound_stillTier3() throws Exception {
        // 越过三档界也写三档（用新版 th3=24，25 不外溢成第 4 档）
        Date at = d("2026-03-10 10:00:00");
        assertEquals(3, service.evaluate("DIVORCE_ADJ", new BigDecimal("25"), at));
    }

    @Test
    void level_missingThirdBound_isNoCap_notTier4() throws Exception {
        // 旧版 th3 缺格=不设界，顺二档往下走归三档，不是断档、不是第 4 档
        Date at = d("2026-02-15 10:00:00");
        assertEquals(3, service.evaluate("DIVORCE_ADJ", new BigDecimal("500"), at));
    }

    // ===== 时点取用：补做讨结论按做成之日请版本 =====

    @Test
    void locate_beforeHandover_usesOldVersion() throws Exception {
        TMarrRiskLine r = service.locate("DIVORCE_ADJ", d("2026-02-28 23:59:59"));
        assertEquals(10L, r.getId());
    }

    @Test
    void locate_handoverDay_belongsToNewVersion() throws Exception {
        // 交棒当日归新版：[start,end) 半开区间
        TMarrRiskLine r = service.locate("DIVORCE_ADJ", d("2026-03-01 00:00:00"));
        assertEquals(11L, r.getId());
    }

    @Test
    void backfill_oldMonth_gradedByOldRule() throws Exception {
        // 腊月做成、开春讨结论：仍按腊月站着的旧版算（旧版界 10/20）
        RiskGradeResult result = service.evaluate(
                new RiskGradeQuery("DIVORCE_ADJ", new BigDecimal("10"), d("2026-02-20 09:00:00")));
        assertTrue(result.isGraded());
        assertEquals(2, result.getLevel());
        assertEquals("DIVORCE_ADJ", result.getRuleCode());
        assertEquals(d("2025-01-01 00:00:00"), result.getEffStart());
    }

    @Test
    void locate_beforeAnyVersion_isNoRule() throws Exception {
        assertNull(service.locate("DIVORCE_ADJ", d("2024-12-31 23:59:59")));
        assertEquals(0, service.evaluate("DIVORCE_ADJ", new BigDecimal("10"), d("2024-12-31 23:59:59")));
    }

    @Test
    void evaluate_noRule_reportsExplicitly() throws Exception {
        RiskGradeResult r = service.evaluate(
                new RiskGradeQuery("DIVORCE_ADJ", new BigDecimal("10"), d("2024-01-01 00:00:00")));
        assertFalse(r.isGraded());
        assertEquals(RiskGradeResult.NO_RULE, r.getReasonCode());
    }

    @Test
    void voidedRule_neverUsable_evenOnNeighboringDays() throws Exception {
        // 作废条：停用且无交棒日，隔三差五邻近日子也不许拿来顶
        assertFalse(service.usable(40L, d("2025-06-01 00:00:00")));
        assertNull(service.locate("PROPERTY", d("2025-06-01 00:00:00")));
    }

    // ===== 抢档：顺位大者先说话，并列取代号在后者 =====

    @Test
    void evaluateTop_equalPriority_picksLaterCode() throws Exception {
        // VIOLENCE 与 MINOR_IMPACT 同为 20，V 在 M 之后 -> VIOLENCE 先说话
        RiskGradeResult r = service.evaluate(
                new RiskGradeQuery(null, new BigDecimal("12"), d("2025-07-01 00:00:00")));
        assertTrue(r.isGraded());
        assertEquals("VIOLENCE", r.getRuleCode());
        assertEquals(3, r.getLevel());
    }

    @Test
    void evaluateTop_voidedHighPriority_isSkipped() throws Exception {
        // PROPERTY 顺位 30 最高但已作废，不得顶；落到顺位 20 的 VIOLENCE
        assertEquals("VIOLENCE",
                service.evaluate(new RiskGradeQuery(null, new BigDecimal("6"), d("2025-07-01 00:00:00")))
                        .getRuleCode());
    }

    @Test
    void evaluateTop_notYetEffective_excluded() throws Exception {
        // 2025-06-01 之前 MINOR_IMPACT 尚未启用；跨代号抢档只剩 VIOLENCE 在任
        assertEquals("VIOLENCE",
                service.evaluate(new RiskGradeQuery(null, new BigDecimal("3"), d("2025-05-01 00:00:00")))
                        .getRuleCode());
    }

    // ===== 分值不成体统：不硬算，退回重填 =====

    @Test
    void illegal_negative_returnsZeroAndReason() throws Exception {
        Date at = d("2026-03-10 00:00:00");
        assertEquals(0, service.evaluate("DIVORCE_ADJ", new BigDecimal("-1"), at));
        RiskGradeResult r = service.evaluate(new RiskGradeQuery("DIVORCE_ADJ", new BigDecimal("-1"), at));
        assertEquals(RiskGradeResult.ILLEGAL_SCORE, r.getReasonCode());
        assertFalse(r.isGraded());
    }

    @Test
    void illegal_fourDigitNine_rejected() throws Exception {
        Date at = d("2026-03-10 00:00:00");
        RiskGradeResult r = service.evaluate(new RiskGradeQuery("DIVORCE_ADJ", new BigDecimal("9999"), at));
        assertEquals(RiskGradeResult.ILLEGAL_SCORE, r.getReasonCode());
    }

    @Test
    void legal_upperBound999_isTier3() throws Exception {
        Date at = d("2026-03-10 00:00:00");
        RiskGradeResult r = service.evaluate(new RiskGradeQuery("DIVORCE_ADJ", new BigDecimal("999"), at));
        assertTrue(r.isGraded());
        assertEquals(3, r.getLevel());
    }

    @Test
    void nullDate_doesNotSilentlyUseToday() {
        // 没有做成之日不得默认拿今天桌上这一版
        RiskGradeResult r = service.evaluate(new RiskGradeQuery("DIVORCE_ADJ", new BigDecimal("10"), null));
        assertEquals(RiskGradeResult.NO_RULE, r.getReasonCode());
    }

    @Test
    void versions_listAllEditableHistoryInDescStartOrder() throws Exception {
        List<TMarrRiskLine> versions = service.listVersions("DIVORCE_ADJ");
        assertEquals(2, versions.size());
        // 新版在前，旧版在后，一页页往后翻
        assertEquals(11L, versions.get(0).getId());
        assertEquals(10L, versions.get(1).getId());
    }
}

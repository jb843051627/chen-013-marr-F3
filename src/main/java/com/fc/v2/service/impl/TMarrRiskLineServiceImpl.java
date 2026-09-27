package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMarrRiskLineMapper;
import com.fc.v2.model.auto.TMarrRiskLine;
import com.fc.v2.model.custom.RiskGradeQuery;
import com.fc.v2.model.custom.RiskGradeResult;
import com.fc.v2.service.ITMarrRiskLineService;

/**
 * 婚姻家庭辅导风险评估判据线 Service业务层处理（rule-eval 形状：只取用、算档，无增删改）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMarrRiskLineServiceImpl implements ITMarrRiskLineService {

    /** 分值体统的下界：不许负数 */
    private static final BigDecimal SCORE_MIN = BigDecimal.ZERO;
    /** 分值体统的上界：四位九（9999）这一类四位分数不成体统，最高只认到 999 */
    private static final BigDecimal SCORE_MAX = new BigDecimal("999");

    /** 抢档顺位：顺位大者先说话；顺位并列，取代号在后（字典序靠后）的一条 */
    private static final Comparator<TMarrRiskLine> PRIORITY_THEN_CODE_DESC = new Comparator<TMarrRiskLine>() {
        @Override
        public int compare(TMarrRiskLine a, TMarrRiskLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            int byCode = cb.compareTo(ca);
            if (byCode != 0) {
                return byCode;
            }
            // 同代号同日重叠不应出现（交接口径只许一套）；万一脏数据，启用晚的在先，保持确定性
            Date sa = a.getEffStart();
            Date sb = b.getEffStart();
            if (sa == null && sb == null) {
                return 0;
            }
            if (sa == null) {
                return 1;
            }
            if (sb == null) {
                return -1;
            }
            return sb.compareTo(sa);
        }
    };

    @javax.annotation.Resource
    private TMarrRiskLineMapper marrRiskLineMapper;

    @Override
    public TMarrRiskLine selectTMarrRiskLineById(Long id) {
        return this.marrRiskLineMapper.selectById(id);
    }

    @Override
    public List<TMarrRiskLine> selectTMarrRiskLineList(QueryWrapper<TMarrRiskLine> queryWrapper) {
        return this.marrRiskLineMapper.selectList(queryWrapper);
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (!scoreWellFormed(input)) {
            return 0;
        }
        TMarrRiskLine rule = locate(ruleCode, at);
        if (rule == null) {
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null || !scoreWellFormed(input)) {
            return 0;
        }
        List<TMarrRiskLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return levelOf(avail.get(0), input);
    }

    @Override
    public RiskGradeResult evaluate(RiskGradeQuery query) {
        if (query == null) {
            return RiskGradeResult.illegalScore(null, null);
        }
        BigDecimal score = query.getScore();
        Date at = query.getEvaluatedAt();
        if (score == null || !scoreWellFormed(score)) {
            return RiskGradeResult.illegalScore(score, at);
        }
        if (at == null) {
            // 没有评估做成之日便无从请版本，按无判据可依直说，不许默认拿今日这一版
            return RiskGradeResult.noRule(query.getRuleCode(), score, null);
        }
        String code = query.getRuleCode();
        TMarrRiskLine rule;
        if (code == null || code.trim().isEmpty()) {
            List<TMarrRiskLine> avail = listAvailable(at);
            if (avail.isEmpty()) {
                return RiskGradeResult.noRule(null, score, at);
            }
            rule = avail.get(0);
        } else {
            rule = locate(code, at);
            if (rule == null) {
                return RiskGradeResult.noRule(code, score, at);
            }
        }
        return RiskGradeResult.ok(levelOf(rule, score), rule, score, at);
    }

    @Override
    public List<TMarrRiskLine> listAvailable(Date at) {
        if (at == null) {
            return new ArrayList<TMarrRiskLine>();
        }
        List<TMarrRiskLine> all = this.marrRiskLineMapper.selectList(
                new QueryWrapper<TMarrRiskLine>().eq("del_flag", 0));
        List<TMarrRiskLine> hit = new ArrayList<TMarrRiskLine>();
        for (TMarrRiskLine r : all) {
            if (inOffice(r, at)) {
                hit.add(r);
            }
        }
        Collections.sort(hit, PRIORITY_THEN_CODE_DESC);
        return hit;
    }

    @Override
    public List<TMarrRiskLine> listVersions(String ruleCode) {
        if (ruleCode == null || ruleCode.trim().isEmpty()) {
            return new ArrayList<TMarrRiskLine>();
        }
        List<TMarrRiskLine> rows = this.marrRiskLineMapper.selectList(new QueryWrapper<TMarrRiskLine>()
                .eq("del_flag", 0)
                .eq("rule_code", ruleCode));
        // 含已让位（status=1）的历代版本，按启用之日倒序：新版在前头，旧版一页页往后翻
        Collections.sort(rows, new Comparator<TMarrRiskLine>() {
            @Override
            public int compare(TMarrRiskLine a, TMarrRiskLine b) {
                Date sa = a.getEffStart();
                Date sb = b.getEffStart();
                if (sa == null && sb == null) {
                    return 0;
                }
                if (sa == null) {
                    return 1;
                }
                if (sb == null) {
                    return -1;
                }
                return sb.compareTo(sa);
            }
        });
        return rows;
    }

    @Override
    public TMarrRiskLine locate(String ruleCode, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || at == null) {
            return null;
        }
        List<TMarrRiskLine> hit = this.marrRiskLineMapper.selectList(new QueryWrapper<TMarrRiskLine>()
                .eq("del_flag", 0)
                .eq("rule_code", ruleCode));
        List<TMarrRiskLine> standing = new ArrayList<TMarrRiskLine>();
        for (TMarrRiskLine r : hit) {
            if (inOffice(r, at)) {
                standing.add(r);
            }
        }
        if (standing.isEmpty()) {
            // 直说无判据可依；隔三差五邻近日子上的那几条不许拿来顶
            return null;
        }
        Collections.sort(standing, PRIORITY_THEN_CODE_DESC);
        return standing.get(0);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TMarrRiskLine r = this.marrRiskLineMapper.selectById(id);
        return r != null && inOffice(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    /**
     * 在任口径（辅导室核查与处里抽查共用这一套，不许各讲各的）：
     * 未逻辑删除；评估做成之日落在 [启用之日, 交棒之日) 内——交棒当天归新版管，
     * 旧版管到交棒前一刻为止。记号为"让位(status=1)"且已记交棒日的旧版，
     * 在其 [启用,交棒) 当年仍是在任的一条——腊月做的评估开春讨结论，仍按腊月站着的它算；
     * 唯独"停用又没记交棒日(eff_end 为空)"的作废条，任何日子都不认。
     */
    private boolean inOffice(TMarrRiskLine r, Date at) {
        if (r == null) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag() != 0) {
            return false;
        }
        Integer status = r.getStatus();
        if (status == null) {
            return false;
        }
        if (status != 0 && r.getEffEnd() == null) {
            // 停用却没有交棒日：作废条，窗口开着也不认
            return false;
        }
        Date start = r.getEffStart();
        Date end = r.getEffEnd();
        if (start != null && at.before(start)) {
            return false;
        }
        if (end != null && !at.before(end)) {
            return false;
        }
        return true;
    }

    /** 分值成体统：0..999。负数、四位九（9999 这一类四位分数）一律打回，不硬算 */
    private boolean scoreWellFormed(BigDecimal score) {
        return score != null
                && score.compareTo(SCORE_MIN) >= 0
                && score.compareTo(SCORE_MAX) <= 0;
    }

    /**
     * 算档：拿分值够三道界，从高往低够——挨着界就算高档。
     * 分值正与二档界一般高，写三档；越过三档界的，也写三档（三档只封上、不外溢）。
     * 第三格（三档上界）从纸质评分表誊来时常空着：空=不设界，顺着二档往下走归入三档，
     * 不是没人认领的断档，故本判定根本不拿 th3Max 往下压档。
     */
    private int levelOf(TMarrRiskLine rule, BigDecimal score) {
        BigDecimal th2 = rule.getTh2Max();
        if (th2 != null && score.compareTo(th2) >= 0) {
            return 3;
        }
        BigDecimal th1 = rule.getTh1Max();
        if (th1 != null && score.compareTo(th1) >= 0) {
            return 2;
        }
        return 1;
    }
}

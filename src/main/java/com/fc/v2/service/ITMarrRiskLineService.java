package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TMarrRiskLine;
import com.fc.v2.model.custom.RiskGradeQuery;
import com.fc.v2.model.custom.RiskGradeResult;

/**
 * 婚姻家庭辅导风险评估判据线 Service接口（rule-eval 形状：只给取用与算档，不配新建/修改/删除）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMarrRiskLineService {

    /** 按主键查询判据 */
    TMarrRiskLine selectTMarrRiskLineById(Long id);

    /** 台账翻查（只读）：历代版本含已让位者都列得出，供一页页翻回 */
    List<TMarrRiskLine> selectTMarrRiskLineList(QueryWrapper<TMarrRiskLine> queryWrapper);

    /**
     * 单条判档（原判定方法，入参与返回照旧）：按 ruleCode 请出 at 时刻在任的那一版，
     * 拿分值够三道界定档，返回 1/2/3。
     * 无在任版本 / 分值不成体统 / 参数缺失，一律返回 0。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /**
     * 跨代号抢档判档（原方法照旧）：at 时刻在任判据中顺位大者先说话，
     * 顺位相同取代号在后者；无在任判据返回 0。
     */
    int evaluateTop(BigDecimal input, Date at);

    /**
     * 判档（扩用同名重载）：口径与原方法同一套，但把结论压死了再给回——
     * 档位、认下的判据代号、该版启用之日一并封在 {@link RiskGradeResult} 里。
     * query.ruleCode 留空即跨代号抢档。落到哪一档、认哪一条，全以本方法给回为准，
     * 页面不许先摆结果再凑；该日无在任版本给回 NO_RULE，不拿邻近日子的版本顶。
     */
    RiskGradeResult evaluate(RiskGradeQuery query);

    /** at 时刻在任判据（顺位降序、代号降序），状态须为现行且窗口罩得住 at */
    List<TMarrRiskLine> listAvailable(Date at);

    /**
     * 同一代号历代版本（含已让位者），按启用之日倒序，供一页页翻回核对；
     * 只查不删，已逻辑删除的不与见面。
     */
    List<TMarrRiskLine> listVersions(String ruleCode);

    /**
     * 定位某代号在 at 时刻站着的那一条；没有就返回 null。
     * 只认窗口严丝合缝罩得住 at 的现行判据，隔三差五邻近日子的不许拿来顶。
     */
    TMarrRiskLine locate(String ruleCode, Date at);

    /** 单条判据在 at 时刻是否在任（批量导入/报表等旁路复用，口径与定位/列表一致） */
    boolean usable(Long id, Date at);

    /** at 时刻在任判据条数（列表页角标 / 看板） */
    int countAvailable(Date at);
}

package com.fc.v2.controller.admin;

import java.util.Date;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TMarrRiskLine;
import com.fc.v2.model.custom.RiskGradeQuery;
import com.fc.v2.model.custom.RiskGradeResult;
import com.fc.v2.service.ITMarrRiskLineService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 婚姻家庭辅导风险评估判据线 Controller（rule-eval 形状：只给取用与算档）
 *
 * <p>本模块不挂新建、修改、删除入口。落到哪一档、认哪一条，全以业务服务层
 * {@link ITMarrRiskLineService#evaluate(RiskGradeQuery)} 的给回为准，
 * 页面不许先摆一个结果再去凑。</p>
 *
 * @author fuce
 * @date 2026-09-27
 */
@Api(value = "婚姻家庭辅导风险评估判据线")
@Controller
@RequestMapping("/marrRiskLine")
public class MarrRiskLineController extends BaseController {

    @Autowired
    private ITMarrRiskLineService marrRiskLineService;

    @Log(title = "辅导风险判据历代版本台账", action = "list")
    @ApiOperation(value = "版本台账(分页)", notes = "历代版本含已让位者都列得出，一页页翻回")
    @GetMapping("/list")
    @RequiresPermissions("marrRiskLine:list")
    @ResponseBody
    public ResultTable list(TMarrRiskLine record) {
        QueryWrapper<TMarrRiskLine> queryWrapper = new QueryWrapper<TMarrRiskLine>();
        queryWrapper.eq("del_flag", 0);
        if (record.getRuleCode() != null && !record.getRuleCode().trim().isEmpty()) {
            queryWrapper.eq("rule_code", record.getRuleCode().trim());
        }
        queryWrapper.orderByAsc("rule_code").orderByDesc("eff_start");
        startPage();
        com.github.pagehelper.PageInfo<TMarrRiskLine> page =
                new com.github.pagehelper.PageInfo<TMarrRiskLine>(
                        marrRiskLineService.selectTMarrRiskLineList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "辅导风险判据历代版本翻查", action = "versions")
    @ApiOperation(value = "同代号历代版本", notes = "按启用之日倒序，含已让位者，供翻回核对")
    @GetMapping("/versions")
    @RequiresPermissions("marrRiskLine:list")
    @ResponseBody
    public AjaxResult versions(@RequestParam("ruleCode") String ruleCode) {
        return AjaxResult.successData(200, marrRiskLineService.listVersions(ruleCode));
    }

    @Log(title = "辅导风险判据定位", action = "locate")
    @ApiOperation(value = "定位某日在任判据", notes = "辅导室核查与处里抽查共用此口，口径只许一套")
    @GetMapping("/locate")
    @RequiresPermissions("marrRiskLine:list")
    @ResponseBody
    public AjaxResult locate(@RequestParam("ruleCode") String ruleCode,
                             @RequestParam("at") Date at) {
        TMarrRiskLine rule = marrRiskLineService.locate(ruleCode, at);
        if (rule == null) {
            // 直说无判据可依，不拿邻近日子的版本顶
            return AjaxResult.success(200, "该日无在任判据可依", null);
        }
        return AjaxResult.success(200, "操作成功", rule);
    }

    @Log(title = "辅导风险判档", action = "judge")
    @ApiOperation(value = "判档取结论", notes = "结论压死：档位+判据代号+该版启用之日一并给回")
    @PostMapping("/judge")
    @RequiresPermissions("marrRiskLine:judge")
    @ResponseBody
    public AjaxResult judge(RiskGradeQuery query) {
        RiskGradeResult result = marrRiskLineService.evaluate(query);
        // 无判据可依 / 分值不成体统都在结论的事由里明说，不作 HTTP 异常，页面照实给辅导员看
        return AjaxResult.success(200, result.getReason(), result);
    }

    @ApiOperation(value = "某日在任判据条数", notes = "列表页角标/看板")
    @GetMapping("/countAvailable")
    @RequiresPermissions("marrRiskLine:list")
    @ResponseBody
    public AjaxResult countAvailable(@RequestParam("at") Date at) {
        return AjaxResult.successData(200, marrRiskLineService.countAvailable(at));
    }
}

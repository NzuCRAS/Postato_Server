// 逻辑层:开发进度树操作。数据源是需求详情的 devPlan,变更后回调 onChanged 刷新。
import { useState } from 'react'
import { message } from 'antd'
import {
  addCorrection,
  createDevPlan,
  resolveCorrection,
  updateDevPlanNode,
  type NodeInput,
  type NodeUpdate,
} from '../api/devplan'
import type { DevPlanRepo, StructuredModule } from '../types'

export function useDevPlan(reqId: string, onChanged: () => Promise<void> | void) {
  const [busy, setBusy] = useState(false)

  const run = async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
    setBusy(true)
    try {
      const r = await fn()
      await onChanged()
      return r
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
      return undefined
    } finally {
      setBusy(false)
    }
  }

  // 基于需求 structured.modules 预生成初始树(每模块一个一级节点,继承验收标准)
  const generateFromModules = async (modules: StructuredModule[], repo?: DevPlanRepo): Promise<void> => {
    const nodes: NodeInput[] = (modules ?? [])
      .filter((m) => m.name)
      .map((m) => ({
        title: m.name,
        description: m.description,
        module_ref: m.name,
        acceptance_criteria: m.acceptance_criteria ?? [],
      }))
    await run(() =>
      createDevPlan(reqId, nodes.length ? nodes : [{ title: '根任务' }], {
        repo: repo?.url ? repo : undefined,
      }),
    )
  }

  // 更新节点;成功返回软警告数组(可能为空),失败返回 undefined(错误已由 run 弹出)
  const updateNode = async (nodeId: string, updates: NodeUpdate): Promise<string[] | undefined> => {
    const res = await run(() => updateDevPlanNode(reqId, nodeId, updates))
    return res ? (res.warnings ?? []) : undefined
  }

  const leaveCorrection = (nodeId: string, msg: string) =>
    run(() => addCorrection(reqId, nodeId, msg))

  const markCorrectionResolved = (nodeId: string, cid: string) =>
    run(() => resolveCorrection(reqId, nodeId, cid))

  return { busy, generateFromModules, updateNode, leaveCorrection, markCorrectionResolved }
}

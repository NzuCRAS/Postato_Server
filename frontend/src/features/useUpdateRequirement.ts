// 逻辑层:更新需求
import { useState } from 'react'
import { updateRequirement, type RequirementInput } from '../api/requirement'
import type { Requirement } from '../types'

export function useUpdateRequirement() {
  const [submitting, setSubmitting] = useState(false)

  const update = async (id: string, input: RequirementInput): Promise<Requirement> => {
    setSubmitting(true)
    try {
      return await updateRequirement(id, input)
    } finally {
      setSubmitting(false)
    }
  }

  return { submitting, update }
}

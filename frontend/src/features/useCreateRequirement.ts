// 逻辑层:创建需求
import { useState } from 'react'
import { createRequirement, type RequirementInput } from '../api/requirement'
import type { Requirement } from '../types'

export function useCreateRequirement() {
  const [submitting, setSubmitting] = useState(false)

  const create = async (input: RequirementInput): Promise<Requirement> => {
    setSubmitting(true)
    try {
      return await createRequirement(input)
    } finally {
      setSubmitting(false)
    }
  }

  return { submitting, create }
}

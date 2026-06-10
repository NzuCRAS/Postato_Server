// 视图层:新建需求(整页 RequirementForm)
import { message } from 'antd'
import { useNavigate } from 'react-router-dom'
import RequirementForm, { type RequirementFormValues } from '../components/RequirementForm'
import { useCreateRequirement } from '../features/useCreateRequirement'
import { useProjects } from '../features/ProjectContext'

export default function RequirementCreatePage() {
  const navigate = useNavigate()
  const { submitting, create } = useCreateRequirement()
  const { currentId } = useProjects()

  const onSubmit = async (v: RequirementFormValues, status?: string) => {
    try {
      const created = await create({ ...v, status, projectId: currentId ?? undefined })
      message.success('创建成功')
      navigate(`/requirements/${created.id}`)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '创建失败')
    }
  }

  return (
    <RequirementForm
      mode="create"
      submitting={submitting}
      onSubmit={onSubmit}
      onCancel={() => navigate('/requirements')}
    />
  )
}

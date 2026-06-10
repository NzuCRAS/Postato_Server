// 视图层:编辑需求(整页 RequirementForm)
import { Alert, Spin, message } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import RequirementForm, { type RequirementFormValues } from '../components/RequirementForm'
import { useRequirementDetail } from '../features/useRequirementDetail'
import { useUpdateRequirement } from '../features/useUpdateRequirement'

export default function RequirementEditPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { data, loading, error } = useRequirementDetail(id)
  const { submitting, update } = useUpdateRequirement()

  if (loading) return <Spin style={{ display: 'block', marginTop: 80 }} />
  if (error) return <Alert type="error" message={error} />
  if (!data) return null

  const onSubmit = async (v: RequirementFormValues) => {
    try {
      await update(id, v)
      message.success('已保存')
      navigate(`/requirements/${id}`)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    }
  }

  return (
    <RequirementForm
      mode="edit"
      initial={data}
      submitting={submitting}
      onSubmit={onSubmit}
      onCancel={() => navigate(`/requirements/${id}`)}
    />
  )
}

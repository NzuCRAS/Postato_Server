import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Spin } from 'antd'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { ProjectProvider } from './features/ProjectContext'
import LoginPage from './pages/LoginPage'
import SettingsPage from './pages/SettingsPage'
import ProjectsPage from './pages/ProjectsPage'
import ProjectDetailPage from './pages/ProjectDetailPage'
import RequirementListPage from './pages/RequirementListPage'
import RequirementCreatePage from './pages/RequirementCreatePage'
import RequirementEditPage from './pages/RequirementEditPage'
import RequirementDetailPage from './pages/RequirementDetailPage'
import WikiPage from './pages/WikiPage'
import WikiEditPage from './pages/WikiEditPage'
import AssetsPage from './pages/AssetsPage'
import AppLayout from './components/AppLayout'

function ProtectedRoutes() {
  const { user, loading } = useAuth()
  if (loading) return <Spin style={{ display: 'block', marginTop: 120 }} />
  if (!user) return <Navigate to="/login" replace />
  return (
    <ProjectProvider>
      <AppLayout />
    </ProjectProvider>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoutes />}>
            <Route path="/" element={<Navigate to="/projects" replace />} />
            <Route path="/projects" element={<ProjectsPage />} />
            <Route path="/projects/:id" element={<ProjectDetailPage />} />
            <Route path="/requirements" element={<RequirementListPage />} />
            <Route path="/requirements/new" element={<RequirementCreatePage />} />
            <Route path="/requirements/:id" element={<RequirementDetailPage />} />
            <Route path="/requirements/:id/edit" element={<RequirementEditPage />} />
            <Route path="/wiki" element={<WikiPage />} />
            <Route path="/wiki/new" element={<WikiEditPage />} />
            <Route path="/wiki/:id/edit" element={<WikiEditPage />} />
            <Route path="/assets" element={<AssetsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}

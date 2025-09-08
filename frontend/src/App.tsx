import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { Layout, theme } from 'antd'

import AppHeader from '@/components/layout/AppHeader'
import AppSider from '@/components/layout/AppSider'
import ProtectedRoute from '@/components/auth/ProtectedRoute'

// Pages
import LoginPage from '@/pages/auth/LoginPage'
import DashboardPage from '@/pages/dashboard/DashboardPage'
import RiskTransactionsPage from '@/pages/risk/RiskTransactionsPage'
import RiskAddressesPage from '@/pages/risk/RiskAddressesPage'
import AlertsPage from '@/pages/alerts/AlertsPage'
import RulesPage from '@/pages/rules/RulesPage'
import ReportsPage from '@/pages/reports/ReportsPage'
import UsersPage from '@/pages/users/UsersPage'
import SettingsPage from '@/pages/settings/SettingsPage'

import { useAuthStore } from '@/store/authStore'

const { Content } = Layout
const { useToken } = theme

const App: React.FC = () => {
  const { token } = useToken()
  const { user } = useAuthStore()

  const isAuthenticated = !!user

  if (!isAuthenticated) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <AppSider />
      <Layout>
        <AppHeader />
        <Content
          style={{
            margin: '16px',
            padding: '24px',
            background: token.colorBgContainer,
            borderRadius: token.borderRadiusLG,
            overflow: 'auto',
          }}
        >
          <Routes>
            {/* 默认重定向到仪表盘 */}
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            
            {/* 仪表盘 */}
            <Route 
              path="/dashboard" 
              element={
                <ProtectedRoute>
                  <DashboardPage />
                </ProtectedRoute>
              } 
            />
            
            {/* 风险管理 */}
            <Route 
              path="/risk/transactions" 
              element={
                <ProtectedRoute permission="risk.view">
                  <RiskTransactionsPage />
                </ProtectedRoute>
              } 
            />
            <Route 
              path="/risk/addresses" 
              element={
                <ProtectedRoute permission="risk.view">
                  <RiskAddressesPage />
                </ProtectedRoute>
              } 
            />
            
            {/* 告警管理 */}
            <Route 
              path="/alerts" 
              element={
                <ProtectedRoute permission="alert.view">
                  <AlertsPage />
                </ProtectedRoute>
              } 
            />
            
            {/* 规则引擎 */}
            <Route 
              path="/rules" 
              element={
                <ProtectedRoute permission="rule.view">
                  <RulesPage />
                </ProtectedRoute>
              } 
            />
            
            {/* 报告中心 */}
            <Route 
              path="/reports" 
              element={
                <ProtectedRoute permission="report.view">
                  <ReportsPage />
                </ProtectedRoute>
              } 
            />
            
            {/* 用户管理 */}
            <Route 
              path="/users" 
              element={
                <ProtectedRoute permission="user.view">
                  <UsersPage />
                </ProtectedRoute>
              } 
            />
            
            {/* 系统设置 */}
            <Route 
              path="/settings" 
              element={
                <ProtectedRoute>
                  <SettingsPage />
                </ProtectedRoute>
              } 
            />
            
            {/* 404 */}
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  )
}

export default App
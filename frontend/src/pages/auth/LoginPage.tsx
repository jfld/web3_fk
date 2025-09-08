import React, { useState } from 'react'
import { Navigate } from 'react-router-dom'
import {
  Form,
  Input,
  Button,
  Card,
  Typography,
  Space,
  Alert,
  Checkbox,
  Divider,
} from 'antd'
import {
  UserOutlined,
  LockOutlined,
  SafetyOutlined,
  ShieldOutlined,
} from '@ant-design/icons'

import { useAuthStore, LoginCredentials } from '@/store/authStore'

const { Title, Text, Paragraph } = Typography

const LoginPage: React.FC = () => {
  const [form] = Form.useForm()
  const [needTwoFactor, setNeedTwoFactor] = useState(false)
  
  const { user, login, isLoading, error, clearError } = useAuthStore()

  // 如果已登录，重定向到仪表盘
  if (user) {
    return <Navigate to="/dashboard" replace />
  }

  const handleSubmit = async (values: LoginCredentials) => {
    clearError()
    
    try {
      await login(values)
    } catch (err: any) {
      // 检查是否需要两步验证
      if (err.message.includes('two-factor') || err.message.includes('2FA')) {
        setNeedTwoFactor(true)
      }
    }
  }

  const handleTwoFactorSubmit = async (values: any) => {
    const loginData = {
      ...form.getFieldsValue(),
      twoFactorCode: values.twoFactorCode,
    }
    
    try {
      await login(loginData)
    } catch (err) {
      // 错误会在store中处理
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      padding: '20px',
    }}>
      <Card
        style={{
          width: '100%',
          maxWidth: 400,
          boxShadow: '0 8px 32px rgba(0, 0, 0, 0.1)',
          borderRadius: 12,
        }}
        bodyStyle={{ padding: '40px 32px' }}
      >
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <ShieldOutlined style={{ fontSize: 48, color: '#1890ff', marginBottom: 16 }} />
          <Title level={2} style={{ margin: 0, color: '#262626' }}>
            Web3 风控平台
          </Title>
          <Paragraph style={{ color: '#8c8c8c', margin: '8px 0 0' }}>
            区块链智能风险监控系统
          </Paragraph>
        </div>

        {error && (
          <Alert
            message={error}
            type="error"
            showIcon
            closable
            onClose={clearError}
            style={{ marginBottom: 24 }}
          />
        )}

        {!needTwoFactor ? (
          <Form
            form={form}
            name="login"
            onFinish={handleSubmit}
            autoComplete="off"
            size="large"
          >
            <Form.Item
              name="username"
              rules={[
                { required: true, message: '请输入用户名或邮箱' },
                { min: 3, message: '用户名至少3个字符' },
              ]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder="用户名或邮箱"
                autoComplete="username"
              />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码至少6个字符' },
              ]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="密码"
                autoComplete="current-password"
              />
            </Form.Item>

            <Form.Item>
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Form.Item name="rememberMe" valuePropName="checked" noStyle>
                  <Checkbox>记住我</Checkbox>
                </Form.Item>
                <Button type="link" style={{ padding: 0 }}>
                  忘记密码？
                </Button>
              </Space>
            </Form.Item>

            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                loading={isLoading}
                block
                style={{ height: 44 }}
              >
                登录
              </Button>
            </Form.Item>
          </Form>
        ) : (
          <Form
            name="twoFactor"
            onFinish={handleTwoFactorSubmit}
            autoComplete="off"
            size="large"
          >
            <div style={{ textAlign: 'center', marginBottom: 24 }}>
              <SafetyOutlined style={{ fontSize: 32, color: '#1890ff', marginBottom: 8 }} />
              <Title level={4} style={{ margin: 0 }}>
                两步验证
              </Title>
              <Text type="secondary">
                请输入您的两步验证代码
              </Text>
            </div>

            <Form.Item
              name="twoFactorCode"
              rules={[
                { required: true, message: '请输入验证码' },
                { pattern: /^\d{6}$/, message: '请输入6位数字验证码' },
              ]}
            >
              <Input
                placeholder="6位验证码"
                maxLength={6}
                style={{ textAlign: 'center', fontSize: 18, letterSpacing: 4 }}
              />
            </Form.Item>

            <Form.Item>
              <Space direction="vertical" style={{ width: '100%' }}>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={isLoading}
                  block
                  style={{ height: 44 }}
                >
                  验证并登录
                </Button>
                <Button
                  type="link"
                  onClick={() => setNeedTwoFactor(false)}
                  block
                >
                  返回登录
                </Button>
              </Space>
            </Form.Item>
          </Form>
        )}

        <Divider style={{ margin: '24px 0' }} />

        <div style={{ textAlign: 'center' }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            © 2024 Web3 Risk Platform. 保护您的数字资产安全
          </Text>
        </div>
      </Card>
    </div>
  )
}

export default LoginPage
import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import zhCN from 'antd/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'

import App from './App'
import './index.css'

// 设置 dayjs 中文
dayjs.locale('zh-cn')

// 创建 React Query 客户端
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 5 * 60 * 1000, // 5 分钟
    },
  },
})

// Ant Design 主题配置
const theme = {
  token: {
    colorPrimary: '#1890ff',
    colorSuccess: '#52c41a',
    colorWarning: '#faad14',
    colorError: '#ff4d4f',
    borderRadius: 6,
  },
  components: {
    Layout: {
      headerBg: '#001529',
      siderBg: '#001529',
    },
  },
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <ConfigProvider 
          locale={zhCN} 
          theme={theme}
        >
          <App />
        </ConfigProvider>
      </QueryClientProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
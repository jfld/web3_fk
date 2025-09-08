import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface User {
  id: number
  username: string
  email: string
  fullName?: string
  avatarUrl?: string
  roles: string[]
  permissions: string[]
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'SUSPENDED'
  twoFactorEnabled: boolean
  lastLoginAt?: string
  createdAt: string
}

export interface AuthState {
  user: User | null
  token: string | null
  refreshToken: string | null
  isLoading: boolean
  error: string | null
}

export interface AuthActions {
  login: (credentials: LoginCredentials) => Promise<void>
  logout: () => void
  refreshAuth: () => Promise<void>
  updateUser: (user: Partial<User>) => void
  clearError: () => void
  setLoading: (loading: boolean) => void
}

export interface LoginCredentials {
  username: string
  password: string
  rememberMe?: boolean
  twoFactorCode?: string
}

export interface LoginResponse {
  user: User
  accessToken: string
  refreshToken: string
  expiresIn: number
}

type AuthStore = AuthState & AuthActions

const useAuthStore = create<AuthStore>()(
  persist(
    (set, get) => ({
      // 初始状态
      user: null,
      token: null,
      refreshToken: null,
      isLoading: false,
      error: null,

      // Actions
      login: async (credentials: LoginCredentials) => {
        set({ isLoading: true, error: null })
        
        try {
          const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify(credentials),
          })

          if (!response.ok) {
            const errorData = await response.json()
            throw new Error(errorData.message || '登录失败')
          }

          const data: LoginResponse = await response.json()
          
          set({
            user: data.user,
            token: data.accessToken,
            refreshToken: data.refreshToken,
            isLoading: false,
            error: null,
          })
        } catch (error) {
          set({
            isLoading: false,
            error: error instanceof Error ? error.message : '登录失败',
          })
          throw error
        }
      },

      logout: () => {
        // 调用登出API（可选）
        fetch('/api/auth/logout', {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${get().token}`,
          },
        }).catch(() => {
          // 忽略登出API错误
        })

        set({
          user: null,
          token: null,
          refreshToken: null,
          error: null,
        })
      },

      refreshAuth: async () => {
        const { refreshToken } = get()
        
        if (!refreshToken) {
          throw new Error('No refresh token available')
        }

        try {
          const response = await fetch('/api/auth/refresh', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({ refreshToken }),
          })

          if (!response.ok) {
            throw new Error('Token refresh failed')
          }

          const data: LoginResponse = await response.json()
          
          set({
            user: data.user,
            token: data.accessToken,
            refreshToken: data.refreshToken,
          })
        } catch (error) {
          // 刷新失败，清除认证状态
          get().logout()
          throw error
        }
      },

      updateUser: (userData: Partial<User>) => {
        const { user } = get()
        if (user) {
          set({
            user: { ...user, ...userData },
          })
        }
      },

      clearError: () => {
        set({ error: null })
      },

      setLoading: (loading: boolean) => {
        set({ isLoading: loading })
      },
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({
        user: state.user,
        token: state.token,
        refreshToken: state.refreshToken,
      }),
    }
  )
)

export { useAuthStore }

// 权限检查辅助函数
export const usePermissions = () => {
  const { user } = useAuthStore()
  
  const hasPermission = (permission: string): boolean => {
    return user?.permissions.includes(permission) || false
  }
  
  const hasRole = (role: string): boolean => {
    return user?.roles.includes(role) || false
  }
  
  const hasAnyRole = (roles: string[]): boolean => {
    return roles.some(role => hasRole(role))
  }
  
  const hasAnyPermission = (permissions: string[]): boolean => {
    return permissions.some(permission => hasPermission(permission))
  }
  
  const isSystemAdmin = (): boolean => {
    return hasRole('SYSTEM_ADMIN')
  }
  
  const isOrgAdmin = (): boolean => {
    return hasRole('ORG_ADMIN') || isSystemAdmin()
  }
  
  return {
    hasPermission,
    hasRole,
    hasAnyRole,
    hasAnyPermission,
    isSystemAdmin,
    isOrgAdmin,
  }
}
/// <reference types="vite/client" />

declare module '*.vue' {
  import { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// 第三方样式文件（运行时由 Vite 处理，这里只是让 TS 放行）
declare module '*.css'
declare module '*.scss'

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string
  readonly VITE_API_BASE: string
  readonly VITE_WS_BASE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

import { ElMessage, ElMessageBox } from 'element-plus'

interface DialogOptions {
  title?: string
  content?: string
  positiveText?: string
  negativeText?: string
  onPositiveClick?: () => unknown | Promise<unknown>
}

export function useMessage() {
  return ElMessage
}

export function useDialog() {
  const confirm = async (options: DialogOptions, type: 'warning' | 'error' | 'info' | 'success') => {
    try {
      await ElMessageBox.confirm(options.content || '', options.title || '提示', {
        type,
        confirmButtonText: options.positiveText || '确定',
        cancelButtonText: options.negativeText || '取消',
        customClass: 'mv-message-box',
        closeOnClickModal: false,
      })
      await options.onPositiveClick?.()
    } catch {
      // Cancel and close actions intentionally have no side effects.
    }
  }

  return {
    warning: (options: DialogOptions) => void confirm(options, 'warning'),
    error: (options: DialogOptions) => void confirm(options, 'error'),
    info: (options: DialogOptions) => void confirm(options, 'info'),
    success: (options: DialogOptions) => void confirm(options, 'success'),
  }
}

import { Client } from '@stomp/stompjs'
// @ts-ignore
import SockJS from 'sockjs-client/dist/sockjs'
import { useTaskStore } from './taskStore'
import type { TaskProgress } from '@/types'

let stompClient: Client | null = null
let connected = false

export function connectWebSocket() {
  if (stompClient && connected) return

  const taskStore = useTaskStore()

  stompClient = new Client({
    webSocketFactory: () => new SockJS('/ws/progress'),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      connected = true
      console.log('[WS] Connected')

      stompClient!.subscribe('/topic/task-progress', (message) => {
        try {
          const progress: TaskProgress = JSON.parse(message.body)
          taskStore.updateTask(progress)
        } catch (e) {
          console.error('[WS] Failed to parse message:', e)
        }
      })
    },
    onDisconnect: () => {
      connected = false
      console.log('[WS] Disconnected')
    },
    onStompError: (frame) => {
      console.error('[WS] STOMP error:', frame.headers['message'])
    },
  })

  stompClient.activate()
}

export function disconnectWebSocket() {
  if (stompClient) {
    stompClient.deactivate()
    stompClient = null
    connected = false
  }
}

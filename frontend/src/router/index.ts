import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { connectWebSocket } from '@/stores/websocket'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/',
    component: () => import('@/views/LayoutView.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Gallery',
        component: () => import('@/views/GalleryView.vue'),
      },
      {
        path: 'albums',
        name: 'Albums',
        component: () => import('@/views/AlbumsView.vue'),
      },
      {
        path: 'albums/:id',
        name: 'AlbumDetail',
        component: () => import('@/views/AlbumDetailView.vue'),
      },
      {
        path: 'favorites',
        name: 'Favorites',
        component: () => import('@/views/FavoritesView.vue'),
      },
      {
        path: 'people',
        name: 'People',
        component: () => import('@/views/PeopleView.vue'),
      },
      {
        path: 'search',
        name: 'Search',
        component: () => import('@/views/SearchView.vue'),
      },
      {
        path: 'timeline',
        name: 'Timeline',
        component: () => import('@/views/TimelineView.vue'),
      },
      {
        path: 'baby',
        name: 'BabyAlbum',
        component: () => import('@/views/BabyAlbumView.vue'),
      },
      {
        path: 'more',
        name: 'More',
        component: () => import('@/views/MoreView.vue'),
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/SettingsView.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

let wsInitialized = false

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth !== false && !token) {
    next('/login')
  } else {
    if (token && !wsInitialized) {
      wsInitialized = true
      connectWebSocket()
    }
    next()
  }
})

export default router

import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  withCredentials: true,
  timeout: 120000
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && window.location.pathname !== '/auth') {
      const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
      window.location.assign(`/auth?redirect=${encodeURIComponent(redirect)}`)
    }
    return Promise.reject(error)
  }
)

export default http

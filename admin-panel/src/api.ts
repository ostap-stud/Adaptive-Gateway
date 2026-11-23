const base = (import.meta.env.VITE_API_BASE || '/auth-service').replace(/\/$/, '')
export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message) }
}
export function errorMessage(error: unknown): string { return error instanceof Error ? error.message : 'Не вдалося виконати запит.' }
export async function request<T>(path: string, token: string | null, options: RequestInit = {}, apiBase = base): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${apiBase}${path}`, {
      ...options,
      headers: { Accept: 'application/json, text/plain', ...(options.body ? {'Content-Type': 'application/json'} : {}), ...(token ? {Authorization: `Bearer ${token}`} : {}), ...options.headers },
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, 'Немає з’єднання із сервером. Перевірте Gateway і спробуйте ще раз.')
  }
  const text = await response.text()
  let data: unknown = text
  try { data = text ? JSON.parse(text) : null } catch { /* Login and mutation responses are plain text. */ }
  if (!response.ok) {
    if (response.status === 401 && token) window.dispatchEvent(new Event('auth-expired'))
    const detail = typeof data === 'object' && data && 'message' in data ? String(data.message) : (typeof data === 'string' && !data.startsWith('<') && data.length < 500 ? data : '')
    const descriptions: Record<number, string> = {
      400: 'Перевірте введені дані. Логін або contact ID можуть уже використовуватися.',
      401: 'Невірний логін або пароль, або термін дії сесії завершився.',
      403: 'Доступ заборонено. Для панелі потрібна роль ROLE_INFRA_ADMIN.',
      404: 'Ресурс не знайдено. Перевірте, чи сервіс зареєстровано.',
      409: 'Цикл уже виконується або конфігурація змінилася. Оновіть дані.',
      422: 'Дані не пройшли перевірку сервера.',
      502: 'Gateway або Adaptive Manager тимчасово недоступний.',
      503: 'Adaptive Manager недоступний або ще не налаштований.',
    }
    throw new ApiError(response.status, `${descriptions[response.status] || `Помилка сервера (${response.status}).`}${detail ? ` ${detail}` : ''}`)
  }
  return data as T
}
export const body = (value: unknown) => JSON.stringify(value)
export function sessionInfo(token: string): {login: string; expires: number} | null {
  try {
    const segment = token.split('.')[1]
    const payload = JSON.parse(atob(segment.replace(/-/g, '+').replace(/_/g, '/')))
    if (!Number.isFinite(payload.exp) || typeof payload.sub !== 'string') return null
    return {login: payload.sub, expires: payload.exp * 1000}
  } catch { return null }
}

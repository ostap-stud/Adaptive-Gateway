import type { Candidate, Observation, Operation, Service, Snapshot } from './types'

export function dateTime(value?: string | null): string {
  if (!value) return 'Ще немає даних'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Невідома дата' : new Intl.DateTimeFormat('uk-UA', {day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit',second:'2-digit'}).format(date)
}
export function observationFor(snapshot: Snapshot | null, name: string) { return snapshot?.observations.find(o => o.name === name) }
export function syncResult(item?: Observation) { return item?.sync || item?.auth?.latestEvent }
export function origin(operation: Operation): string {
  const admin = operation.policies.find(p => p.source === 'ADMIN')
  return admin ? (admin.forceOverride ? 'FORCE OVERRIDE' : 'OVERRIDE') : operation.policies.some(p => p.source === 'CENTRAL') ? 'CENTRAL' : operation.source === 'GENERATED' ? 'AUTO' : 'MANUAL'
}
// The API stores INTERNAL as BLOCKED and PERMISSION as AUTHENTICATED + permissions.
// A generic BLOCKED declaration cannot be safely relabelled INTERNAL without evidence.
export function declaredAccess(operation: Operation, snapshot: Snapshot | null): string {
  const declared = operation.policies.find(p => p.source === 'OPENAPI')
  if (declared?.accessType === 'BLOCKED') {
    const item = observationFor(snapshot, operation.serviceName)
    const warning = `${operation.http_method} ${operation.gateway_path}: Internal or unsupported security requirement`
    return item && item.candidate?.specHash === operation.spec_hash && item.warnings.includes(warning) ? 'INTERNAL / BLOCKED' : 'BLOCKED'
  }
  if (operation.permissions.length) return 'PERMISSION'
  return declared?.accessType || operation.effectivePolicy.accessType
}
export interface ChangeSet { added: string[]; changed: string[]; removed: string[]; known: boolean }
export function detectChanges(candidate: Candidate | null | undefined, operations: Operation[] | undefined): ChangeSet {
  if (!candidate || !operations) return {added:[],changed:[],removed:[],known:false}
  const active = operations.filter(o => o.active)
  const key = (method: string, path: string) => `${method} ${path}`
  const current = new Map(active.map(o => [key(o.http_method,o.gateway_path), o]))
  const desired = new Set(candidate.operations.map(o => key(o.httpMethod,o.gatewayPath)))
  const added: string[] = [], changed: string[] = []
  candidate.operations.forEach(o => {
    const id=key(o.httpMethod,o.gatewayPath), old=current.get(id)
    if (!old) { added.push(id); return }
    const policy=old.policies.find(p => p.source==='OPENAPI')
    if (old.source==='GENERATED' && (old.backend_path!==o.backendPath || (old.operation_id || '')!==(o.operationId || '') || policy?.accessType!==o.access ||
      [...old.permissions].sort().join('\n')!==[...(o.permissions || [])].sort().join('\n'))) changed.push(id)
  })
  return {added,changed,removed:active.filter(o=>o.source==='GENERATED' && !desired.has(key(o.http_method,o.gateway_path))).map(o=>key(o.http_method,o.gateway_path)),known:true}
}
// A route is an entry point, not a declaration of the service's base path.
export function basePath(service: Service) {
  return service.routes.find(r=>r.source==='GENERATED' && r.managedKey==='ingress' && r.active && !r.isInternal)?.route || 'Не визначено'
}
export function documentationPath(service: Service): string | null {
  return /^[a-z][a-z0-9-]{0,62}$/.test(service.serviceName)
    ? service.openapiPath || `/${service.serviceName}/v3/api-docs` : null
}
export function statusTone(value?: string): 'green' | 'amber' | 'red' | 'blue' | 'neutral' {
  if (!value) return 'neutral'
  if (['APPLIED','UNCHANGED','NO_CHANGE','ANALYZED','AVAILABLE','PUBLIC','ACTIVE'].includes(value)) return 'green'
  if (['STALE','MISSING','NO_UP_INSTANCE','MULTIPLE_INSTANCES','PENDING_REFRESH','ANALYZED_WITH_BLOCKED_OPERATIONS','OBSERVE','PERMISSION','OVERRIDE'].includes(value)) return 'amber'
  if (/FAIL|UNAVAILABLE|BLOCKED|INVALID|CONFLICT/.test(value)) return 'red'
  if (['APPLY','AUTHENTICATED','AUTOMATIC','AUTO'].includes(value)) return 'blue'
  return 'neutral'
}

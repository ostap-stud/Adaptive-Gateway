import type { GatewayComponent, Role, Route, RouteComponents } from './types'

export interface ComponentInput { name: string; args: Record<string,string> }
export interface ComponentDraft { key: string; name: string; args: string }
export function parseComponent(draft: ComponentDraft): ComponentInput {
  if (!/^[A-Za-z][A-Za-z0-9]{0,100}$/.test(draft.name.trim())) throw new Error('Назва фільтра/предиката має починатися з літери та містити лише літери й цифри.')
  let args: unknown
  try { args=JSON.parse(draft.args) } catch { throw new Error(`Некоректний JSON параметрів ${draft.name}.`) }
  if (!args || typeof args!=='object' || Array.isArray(args) || Object.values(args).some(value=>typeof value!=='string'))
    throw new Error(`Параметри ${draft.name}: потрібен JSON-об’єкт із рядковими значеннями.`)
  return {name:draft.name.trim(),args:args as Record<string,string>}
}
export function hydrateRoute(route:Pick<Route,'roles'|'filters'|'predicates'>,catalog:RouteComponents,roles:Role[]) {
  function component(id:number,items:GatewayComponent[]):ComponentDraft {
    const item=items.find(item=>item.id===id)
    if(!item)throw new Error(`Не отримано параметри компонента #${id}. Оновіть дані перед редагуванням.`)
    return {key:String(id),name:item.name,args:JSON.stringify(item.args,null,2)}
  }
  return {
    filters:route.filters.map(ref=>component(ref.filterId,catalog.filters)),
    predicates:route.predicates.map(ref=>component(ref.predicateId,catalog.predicates)),
    roles:route.roles.map(ref=>{
      const role=roles.find(role=>role.id===ref.roleId)
      if(!role)throw new Error(`Не отримано роль #${ref.roleId}. Оновіть дані.`)
      return role.role
    }),
  }
}
export function swaggerUiUrl(serviceName:string) {
  // Open the native Gateway origin so Swagger Try it out also reaches business routes.
  const gateway=(import.meta.env.VITE_GATEWAY_PUBLIC_URL || 'http://127.0.0.1:8088').replace(/\/$/,'')
  return `${gateway}/webjars/swagger-ui/index.html?urls.primaryName=${encodeURIComponent(serviceName)}`
}

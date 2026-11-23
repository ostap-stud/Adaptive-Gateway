export type ManagementMode = 'AUTOMATIC' | 'MANUAL' | 'DISABLED'
export interface Route { id: number; route: string; routeDesc?: string; serviceId: number | null; isInternal: boolean; order: number | null; source: string; managedKey?: string; active: boolean; isOverridden: boolean; roles: {roleId: number}[]; filters: {filterId: number}[]; predicates: {predicateId: number}[] }
export interface Service { id: number; serviceName: string; serviceDesc?: string; managementMode: ManagementMode; routes: Route[]; configurationVersion: number; openapiPath?: string; openapiHash?: string; lastSyncedAt?: string }
export interface Policy { id: number; source: string; accessType: string; forceOverride: boolean; roles: string[]; permissionGroups: string[][] }
export interface Operation { id: number; service_id: number; gateway_path: string; backend_path: string; http_method: string; operation_id?: string; active: boolean; source: string; spec_hash?: string; permissions: string[]; policies: Policy[]; effectivePolicy: {accessType: string; roleGroups: string[][]}; serviceName: string }
export interface Role { id: number; role: string; roleName: string; application: string; roleDesc?: string }
export interface GatewayComponent { id: number; name: string; args: Record<string,string> }
export interface RouteComponents { filters: GatewayComponent[]; predicates: GatewayComponent[] }
export interface User { id: number; login: string; login_desc?: string; contact_id: number | null; roles: string[] }
export interface Permission { permission: string; roles: string[]; operations: {id: number; service_name: string; gateway_path: string; http_method: string; active: boolean}[] }
export interface DesiredOperation { gatewayPath: string; backendPath: string; httpMethod: string; operationId?: string; access: string; permissions: string[] }
export interface Candidate { specHash: string; openapiPath: string; route: {path: string}; operations: DesiredOperation[] }
export interface SyncResult { action?: string; status: string; requestId?: string; configurationVersion?: number; message?: string }
export interface Observation { name: string; status: string; observedAt: string; instanceId?: string; candidate?: Candidate; warnings: string[]; message?: string; proposedRequest?: unknown; auth?: {status: string; specHash?: string; expectedVersion?: number; latestEvent?: SyncResult; pendingRefresh?: SyncResult[]}; sync?: SyncResult }
export interface Snapshot { mode: 'OBSERVE' | 'APPLY'; registryStatus: string; observedAt?: string; lastSuccessfulPollAt?: string; observations: Observation[]; message?: string }
export type Resource = 'services' | 'routes' | 'users' | 'roles' | 'permissions' | 'adaptive' | 'operations' | 'components'

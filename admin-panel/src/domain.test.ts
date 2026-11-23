import { describe, expect, it } from 'vitest'
import { basePath, documentationPath, declaredAccess, detectChanges, origin } from './domain'
import { sessionInfo } from './api'
import type { Candidate, Operation, Snapshot, Service, Route } from './types'

const operation:Operation={id:1,service_id:2,serviceName:'demo',gateway_path:'/demo/read',backend_path:'/demo/read',http_method:'GET',active:true,source:'GENERATED',operation_id:'read',spec_hash:'hash',permissions:[],policies:[{id:1,source:'OPENAPI',accessType:'PUBLIC',forceOverride:false,roles:[],permissionGroups:[]}],effectivePolicy:{accessType:'PUBLIC',roleGroups:[]}}
const candidate:Candidate={specHash:'new',openapiPath:'/demo/v3/api-docs',route:{path:'/demo/**'},operations:[{gatewayPath:'/demo/read',backendPath:'/demo/read',httpMethod:'GET',operationId:'read',access:'PUBLIC',permissions:[]}]}

describe('candidate comparison',()=>{
  it('does not invent changes after an APPLY has already reconciled Auth',()=>expect(detectChanges(candidate,[operation])).toEqual({added:[],changed:[],removed:[],known:true}))
  it('detects additions, permission changes and deletions by method/path',()=>{
    const result=detectChanges({...candidate,operations:[{...candidate.operations[0],access:'AUTHENTICATED',permissions:['demo.read']},{...candidate.operations[0],gatewayPath:'/demo/new'}]},[operation,{...operation,id:2,gateway_path:'/demo/removed'}])
    expect(result).toEqual({added:['GET /demo/new'],changed:['GET /demo/read'],removed:['GET /demo/removed'],known:true})
  })
  it('deactivates the final generated operation but never proposes removal of manual operations',()=>{
    expect(detectChanges({...candidate,operations:[]},[operation,{...operation,id:2,source:'MANUAL',gateway_path:'/demo/manual'}]).removed).toEqual(['GET /demo/read'])
  })
  it('distinguishes missing observations from a valid empty specification',()=>{
    expect(detectChanges(null,[operation]).known).toBe(false)
    expect(detectChanges({...candidate,operations:[]},[operation]).known).toBe(true)
    expect(detectChanges(candidate,undefined).known).toBe(false)
  })
  it('counts reactivation as an addition and compares HTTP methods separately',()=>{
    expect(detectChanges(candidate,[{...operation,active:false}]).added).toEqual(['GET /demo/read'])
    expect(detectChanges(candidate,[{...operation,http_method:'POST'}]).removed).toEqual(['POST /demo/read'])
  })
})
describe('faithful access labels',()=>{
  it('shows permission declaration separately from an unresolved effective BLOCKED policy',()=>expect(declaredAccess({...operation,permissions:['demo.read'],effectivePolicy:{accessType:'BLOCKED',roleGroups:[]}},null)).toBe('PERMISSION'))
  it('does not call an unsupported BLOCKED requirement INTERNAL without matching observation evidence',()=>{
    const blocked={...operation,policies:[{...operation.policies[0],accessType:'BLOCKED'}]}
    expect(declaredAccess(blocked,null)).toBe('BLOCKED')
    const snapshot:Snapshot={mode:'APPLY',registryStatus:'AVAILABLE',observations:[{name:'demo',observedAt:'2026-11-19T12:00:00Z',status:'ANALYZED',warnings:['GET /demo/read: Internal or unsupported security requirement'],candidate:{...candidate,specHash:'hash'}}]}
    expect(declaredAccess(blocked,snapshot)).toBe('INTERNAL / BLOCKED')
    expect(declaredAccess({...blocked,spec_hash:'stale'},snapshot)).toBe('BLOCKED')
  })
  it('preserves and identifies explicit administrator force overrides',()=>expect(origin({...operation,policies:[...operation.policies,{...operation.policies[0],id:3,source:'ADMIN',forceOverride:true}]})).toBe('FORCE OVERRIDE'))
})
describe('JWT session display',()=>{
  it('decodes the subject and expiration without treating claims as authorization',()=>{
    const token=`header.${btoa(JSON.stringify({sub:'admin',exp:1700000000})).replace(/=/g,'')}.signature`
    expect(sessionInfo(token)).toEqual({login:'admin',expires:1700000000000})
  })
  it('rejects malformed and non-expiring tokens',()=>{
    expect(sessionInfo('not-a-token')).toBeNull()
    expect(sessionInfo(`h.${btoa(JSON.stringify({sub:'admin'}))}.s`)).toBeNull()
  })
})

describe('service namespace and documentation',()=>{
  const route:Route={id:1,route:'/demo/new-route',serviceId:2,isInternal:false,order:0,source:'MANUAL',active:true,isOverridden:false,roles:[],filters:[],predicates:[]}
  const service:Service={id:2,serviceName:'demo',managementMode:'MANUAL',routes:[route],configurationVersion:0}
  it('never invents a base path from the first manual route',()=>{
    expect(basePath(service)).toBe('Не визначено')
    expect(basePath({...service,routes:[{...route,id:2,route:'/another/path'},route]})).toBe('Не визначено')
  })
  it('identifies active generated ingress regardless of ordering and management mode',()=>{
    const ingress={...route,id:3,route:'/demo/**',source:'GENERATED',managedKey:'ingress'}
    expect(basePath({...service,routes:[route,ingress]})).toBe('/demo/**')
    expect(basePath({...service,routes:[{...ingress,active:false},route]})).toBe('Не визначено')
    expect(basePath({...service,routes:[{...ingress,isInternal:true}]})).toBe('Не визначено')
  })
  it('offers documentation before observation, synchronization or route creation',()=>{
    expect(documentationPath({...service,routes:[]})).toBe('/demo/v3/api-docs')
    expect(documentationPath({...service,openapiPath:'/demo/custom/docs'})).toBe('/demo/custom/docs')
    expect(documentationPath({...service,serviceName:'https://external.example'})).toBeNull()
    expect(documentationPath({...service,serviceName:'d'.repeat(64)})).toBeNull()
  })
})

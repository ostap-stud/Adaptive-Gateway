import { describe, expect, it } from 'vitest'
import { hydrateRoute, parseComponent, swaggerUiUrl } from './routeForms'

describe('manual route editing',()=>{
  it('loads current parameter variants without silently dropping existing refs',()=>{
    const route={roles:[{roleId:7}],filters:[{filterId:2}],predicates:[{predicateId:3}]}
    const form=hydrateRoute(route,{filters:[{id:2,name:'RewritePath',args:{regexp:'/a/(?<segment>.*)',replacement:'/${segment}'}}],predicates:[{id:3,name:'Method',args:{methods:'GET,POST'}}]},[{id:7,role:'ROLE_TEST_READER',roleName:'READER',application:'TEST'}])
    expect(parseComponent(form.filters[0])).toEqual({name:'RewritePath',args:{regexp:'/a/(?<segment>.*)',replacement:'/${segment}'}})
    expect(parseComponent(form.predicates[0])).toEqual({name:'Method',args:{methods:'GET,POST'}})
    expect(form.roles).toEqual(['ROLE_TEST_READER'])
  })
  it('refuses edits if a catalog reference could not be resolved',()=>{
    expect(()=>hydrateRoute({roles:[],filters:[{filterId:2}],predicates:[]},{filters:[],predicates:[]},[])).toThrow('Не отримано параметри')
    expect(()=>hydrateRoute({roles:[{roleId:9}],filters:[],predicates:[]},{filters:[],predicates:[]},[])).toThrow('Не отримано роль')
  })
  it('rejects malformed, array and non-string Gateway arguments',()=>{
    for(const args of ['{','[]','null','{"parts":2}']) expect(()=>parseComponent({key:'1',name:'StripPrefix',args})).toThrow()
    expect(parseComponent({key:'1',name:'StripPrefix',args:'{"parts":"2"}'}).args).toEqual({parts:'2'})
  })
  it('links Swagger UI rather than raw OpenAPI and encodes the selected service',()=>{
    expect(swaggerUiUrl('adaptive-demo')).toContain('/webjars/swagger-ui/index.html?urls.primaryName=adaptive-demo')
    expect(swaggerUiUrl('a&url=bad')).toContain('a%26url%3Dbad')
  })
})

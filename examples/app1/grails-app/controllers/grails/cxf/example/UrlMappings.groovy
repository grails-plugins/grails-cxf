package grails.cxf.example

class UrlMappings {
    static mappings = {
        "/$controller/$action?/$id?(.$format)?"{}
        "500"(view: '/error')
        "404"(view: '/notFound')
    }
}

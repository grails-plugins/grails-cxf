package grails.cxf

import grails.plugins.Plugin
import groovy.util.logging.Slf4j
import org.apache.cxf.bus.spring.SpringBus
import org.apache.cxf.transport.servlet.CXFServlet
import org.grails.cxf.utils.EndpointRegistrationUtil
import org.springframework.boot.web.servlet.ServletRegistrationBean

@Slf4j
class CxfGrailsPlugin extends Plugin {

    def grailsVersion = '7.0.0 > *'
    def pluginExcludes = [
            'grails-app/views/error.gsp',
            'grails-app/conf/spring/ApplicationResources.groovy',
            'grails-app/conf/spring/resources.groovy',
            'grails-app/conf/spring/resources.groovy',
            'grails-app/conf/ApplicationResources.groovy',
            'grails-app/init/BootStrap.groovy',
            'grails-app/conf/DataSource.groovy',
            'grails-app/conf/UrlMappings.groovy',
            'grails-app/conf/codenarc.groovy',
            'grails-app/conf/codenarc.ruleset.all.groovy.txt',
            'grails-app/domain/**',
            'grails-app/endpoints/**',
            'grails-app/i18n/**',
            'grails-app/services/org/**',
            'grails-app/views/**',
            'src/main/groovy/org/grails/cxf/test/**',
            'src/main/java/org/grails/cxf/test/**',
            'web-app/**',
            'codenarc.properties'
    ]

    def name = 'cxf'
    def author = 'Grails Plugins'
    def authorEmail = ''
    def title = 'CXF plug-in for Grails'
    def description = 'Brings easy exposure of service and endpoint classes as Apache CXF SOAP Services to Grails.'

    def developers = [
            [name: 'Christian Oestreich', email: 'acetrike@gmail.com'],
            [name: 'Ryan Crum', email: 'ryan.j.crum@gmail.com'],
            [name: 'Ben Doerr', email: 'craftsman@bendoerr.me']]

    def documentation = 'https://github.com/grails-plugins/grails-cxf'
    def license = 'APACHE'
    def issueManagement = [system: 'GITHUB', url: 'https://github.com/grails-plugins/grails-cxf/issues']
    def scm = [url: 'https://github.com/grails-plugins/grails-cxf']

    Closure doWithSpring() {
        { ->

            log.info 'Wiring the cxf plugin'
            String servletMapping = config.cxf.servlet.mapping ?: '/services/*'
            if (!servletMapping.endsWith('/*')) {
                throw new RuntimeException("Custom servlet mapping should end with the suffix '/*' ie. '/webservices/*'")
            }
            cxfServlet(ServletRegistrationBean, new CXFServlet(), servletMapping) {
                loadOnStartup = 1
            }
            log.info("Wired CxfServlet to url $servletMapping")

            cxf(SpringBus)
        }
    }

    void doWithDynamicMethods() {
        // TODO Implement registering dynamic methods to classes (optional)
    }

    void doWithApplicationContext() {
        EndpointRegistrationUtil.wireEndpoints(applicationContext)
    }

    void onChange(Map<String, Object> event) {
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    void onConfigChange(Map<String, Object> event) {
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    void onShutdown(Map<String, Object> event) {
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}

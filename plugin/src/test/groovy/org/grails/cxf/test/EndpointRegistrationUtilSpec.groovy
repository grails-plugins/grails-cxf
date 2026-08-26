package org.grails.cxf.test

import org.apache.cxf.Bus
import org.apache.cxf.bus.spring.SpringBus
import org.grails.cxf.utils.EndpointRegistrationUtil
import org.grails.cxf.utils.GrailsCxfEndpoint
import org.springframework.context.ApplicationContext
import spock.lang.Specification

class EndpointRegistrationUtilSpec extends Specification {

    ApplicationContext applicationContext = Mock(ApplicationContext)
    Bus bus = new SpringBus()

    def "test url generation"() {
        when:
        EndpointRegistrationUtil.wireEndpoints(applicationContext)

        then:
        1 * applicationContext.getBeansWithAnnotation(GrailsCxfEndpoint.class) >> ['fooService': new FooService()]
        1 * applicationContext.getBean(Bus.DEFAULT_BUS_ID) >> bus
    }

    def "test url generation on non-service with no address"() {
        when:
        EndpointRegistrationUtil.wireEndpoints(applicationContext)

        then:
        thrown(RuntimeException)
        1 * applicationContext.getBeansWithAnnotation(GrailsCxfEndpoint.class) >> ['fooClass': new FooClass()]
        1 * applicationContext.getBean(Bus.DEFAULT_BUS_ID) >> bus
    }

    def "test url generation on non-service with address"() {
        when:
        EndpointRegistrationUtil.wireEndpoints(applicationContext)

        then:
        1 * applicationContext.getBeansWithAnnotation(GrailsCxfEndpoint.class) >> ['fooEndpoint': new FooEndpoint()]
        1 * applicationContext.getBean(Bus.DEFAULT_BUS_ID) >> bus
    }
}

@GrailsCxfEndpoint
class FooService {}

@GrailsCxfEndpoint
class FooClass {}

@GrailsCxfEndpoint(address = 'foo')
class FooEndpoint {}

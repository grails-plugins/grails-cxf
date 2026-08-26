package grails.cxf.example

import org.grails.cxf.utils.GrailsCxfEndpoint

import jakarta.jws.WebMethod
import jakarta.jws.WebResult

@GrailsCxfEndpoint
class DemoService {

    @WebMethod
    @WebResult
    String demoMethod() {
        'demo'
    }
}

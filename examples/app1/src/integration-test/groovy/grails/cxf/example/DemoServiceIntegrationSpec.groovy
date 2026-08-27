package grails.cxf.example

import grails.testing.mixin.integration.Integration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Specification

@Integration
class DemoServiceIntegrationSpec extends Specification {

    @Shared
    RestTemplate restTemplate = new RestTemplate()

    private String getBaseUrl() {
        "http://localhost:${serverPort}"
    }

    void "DemoService is published as a SOAP endpoint with a WSDL"() {
        when:
        ResponseEntity<String> response = restTemplate.exchange(
                "${baseUrl}/services/demo?wsdl", HttpMethod.GET, null, String)

        then:
        response.statusCode == HttpStatus.OK
        response.body.contains('wsdl:definitions')
        response.body.contains('demoMethod')
    }
}

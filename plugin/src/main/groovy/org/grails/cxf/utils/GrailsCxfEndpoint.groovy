package org.grails.cxf.utils

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation to be use to expose a Service or Endpoint class as a CXF Service via Grails.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface GrailsCxfEndpoint {
    String address() default ''
    //this is the serviceName (localpart) attribute
    String name() default ''
    //this is the servicePort (localpart) attribute
    String port() default ''
    EndpointType expose() default EndpointType.JAX_WS
    boolean soap12() default false
    String wsdl() default ''
    //Interceptors
    String[] inInterceptors() default []
    String[] outInterceptors() default []
    String[] inFaultInterceptors() default []
    String[] outFaultInterceptors() default []
    GrailsCxfEndpointProperty[] properties() default []
}

@Target(ElementType.METHOD)
@interface GrailsCxfEndpointProperty {
    public String name() default ''
    public String value() default ''
}

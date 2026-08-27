package org.grails.cxf.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils
import org.apache.cxf.Bus
import org.apache.cxf.interceptor.Interceptor
import org.apache.cxf.jaxws.EndpointImpl
import org.apache.cxf.message.Message
import org.grails.core.artefact.ServiceArtefactHandler
import org.springframework.aop.framework.Advised
import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext

import javax.xml.XMLConstants
import javax.xml.namespace.QName
import jakarta.xml.ws.soap.SOAPBinding

@Slf4j
@CompileStatic
class EndpointRegistrationUtil {

    static void wireEndpoints(ApplicationContext context) {

        Map<String, Object> beansWithAnnotation = context.getBeansWithAnnotation(GrailsCxfEndpoint)
        if (beansWithAnnotation) {
            Bus bus = (Bus) context.getBean(Bus.DEFAULT_BUS_ID)
            for (Map.Entry<String, Object> entry : beansWithAnnotation.entrySet()) {
                Object implementor = entry.getValue()
                EndpointImpl endpoint = new EndpointImpl(bus, implementor)
                GrailsCxfEndpoint annotation = getAnnotation(implementor)
                //needs to happen first
                setServiceAndPortName(endpoint, implementor, annotation)
                publishEndpointUrl(endpoint, implementor, annotation)
                //needs to happen last due to changing factory service stuff
                addConfiguredProperties(endpoint, implementor, annotation, context)
            }
        }
    }

    private static void publishEndpointUrl(EndpointImpl endpoint, Object implementor, GrailsCxfEndpoint annotation) {
        String url = getPublishUrl(implementor, annotation)
        log.info('Endpoint [' + implementor.class + '] configured to use url ' + url + '.')
        if (!url || url == '/') {
            throw new RuntimeException('Endpoint could not be wired due to endpoint url not being set.  Check [' + implementor.class + '] to ensure address property is set')
        } else {
            endpoint.publish(url)
        }
    }

    private static void setServiceAndPortName(EndpointImpl endpoint, Object implementor, GrailsCxfEndpoint annotation) {
        if (annotation != null) {
            addServiceName(annotation, implementor, endpoint)
            addPortName(annotation, implementor, endpoint)
        }
    }

    private static void addConfiguredProperties(EndpointImpl endpoint, Object implementor, GrailsCxfEndpoint annotation, ApplicationContext context) {
        assert implementor != null
        if (annotation != null) {
            addSoap12(annotation, endpoint)
            addWsdl(annotation, endpoint)
            addProperties(annotation, implementor, endpoint)
            addInInterceptors(annotation, endpoint, context)
            addInFaultInterceptors(annotation, endpoint, context)
            addOutInterceptors(annotation, endpoint, context)
            addOutFaultInterceptors(annotation, endpoint, context)
        }
    }

    private static GrailsCxfEndpoint getAnnotation(implementor) {
        GrailsCxfEndpoint annotation = null
        if (implementor instanceof Advised) {
            try {
                annotation = ((Advised) implementor).getTargetSource().getTarget().getClass().getAnnotation(GrailsCxfEndpoint)
            } catch (Exception e) {
                log.error('Could not wire AOP Proxied endpoint.', e)
            }
        } else {
            annotation = implementor.getClass().getAnnotation(GrailsCxfEndpoint)
        }
        annotation
    }

    private static void addServiceName(GrailsCxfEndpoint annotation, Object implementor, EndpointImpl endpoint) {
        if (annotation?.name()) {
            endpoint.serviceName =
                    new QName(getNamespaceURI(implementor),
                            annotation.name(),
                            XMLConstants.DEFAULT_NS_PREFIX)
        }
    }

    private static void addPortName(GrailsCxfEndpoint annotation, Object implementor, EndpointImpl endpoint) {
        if (annotation?.port()) {
            endpoint.endpointName =
                    new QName(getNamespaceURI(implementor),
                            annotation.port(),
                            XMLConstants.DEFAULT_NS_PREFIX)
        }
    }

    private static String getNamespaceURI(implementor) {
        'http://' + implementor.getClass().package.name.split('\\.').reverse().join('.')
    }

    private static void addWsdl(GrailsCxfEndpoint annotation, EndpointImpl endpoint) {
        if (annotation.wsdl()) {
            endpoint.getServerFactory().wsdlLocation = annotation.wsdl()
        }
    }

    private static void addSoap12(GrailsCxfEndpoint annotation, EndpointImpl endpoint) {
        if (annotation.soap12()) {
            endpoint.getServerFactory().bindingId = SOAPBinding.SOAP12HTTP_MTOM_BINDING
        }
    }

    private
    static void addInInterceptors(GrailsCxfEndpoint annotation, EndpointImpl endpoint, ApplicationContext context) {
        try {
            for (String inInterceptorName : annotation.inInterceptors()) {
                endpoint.getServer().getEndpoint().getInInterceptors().add(getInterceptor(context, inInterceptorName))
                log.info('Endpoint [' + endpoint.address + '] configured to use in interceptor bean ' + inInterceptorName + '.')
            }
        } catch (BeansException e) {
            log.error('Could not wire in interceptors', e)
        }
    }

    private
    static void addInFaultInterceptors(GrailsCxfEndpoint annotation, EndpointImpl endpoint, ApplicationContext context) {
        try {
            for (String inFaultInterceptorName : annotation.inFaultInterceptors()) {
                endpoint.getServer().getEndpoint().getInFaultInterceptors().add(getInterceptor(context, inFaultInterceptorName))
                log.info('Endpoint [' + endpoint.address + '] configured to use in fault interceptor bean ' + inFaultInterceptorName + '.')
            }
        } catch (BeansException e) {
            log.error('Could not wire in interceptors', e)
        }
    }

    private
    static void addOutInterceptors(GrailsCxfEndpoint annotation, EndpointImpl endpoint, ApplicationContext context) {
        try {
            for (String outInterceptorName : annotation.outInterceptors()) {
                endpoint.getServer().getEndpoint().getOutInterceptors().add(getInterceptor(context, outInterceptorName))
                log.info('Endpoint [' + endpoint.address + '] configured to use out interceptor bean ' + outInterceptorName + '.')
            }
        } catch (BeansException e) {
            log.error('Could not wire out interceptors', e)
        }
    }

    private
    static void addOutFaultInterceptors(GrailsCxfEndpoint annotation, EndpointImpl endpoint, ApplicationContext context) {
        try {
            for (String outFaultInterceptorName : annotation.outFaultInterceptors()) {
                endpoint.getServer().getEndpoint().getOutFaultInterceptors().add(getInterceptor(context, outFaultInterceptorName))
                log.info('Endpoint [' + endpoint.address + '] configured to use out fault interceptor bean ' + outFaultInterceptorName + '.')
            }
        } catch (BeansException e) {
            log.error('Could not wire out fault interceptors', e)
        }
    }

    static Interceptor<? extends Message> getInterceptor(ApplicationContext context, String inInterceptorName) {
        (Interceptor<? extends Message>) context.getBean(inInterceptorName)
    }

    private static void addProperties(GrailsCxfEndpoint annotation, implementor, EndpointImpl endpoint) {
        if (annotation?.properties()?.length > 0) {
            Map<String, Object> properties = [:]
            for (GrailsCxfEndpointProperty prop : annotation.properties()) {
                properties.put(prop.name(), prop.value())
            }
            if (properties) {
                log.info('Endpoint [' + implementor.class + '] configured to use properties ' + properties + '.')
                endpoint.getServerFactory().properties = properties
            }
        }
    }

    private static String getPublishUrl(Object implementor, GrailsCxfEndpoint annotation) {
        assert implementor != null
        String url = ''
        if (implementor instanceof Advised) {
            try {
                annotation = ((Advised) implementor).getTargetSource().getTarget().getClass().getAnnotation(GrailsCxfEndpoint)
                if (annotation != null) {
                    url = annotation.address()
                }
            } catch (Exception e) {
                log.error('Could not wire AOP Proxied smart api endpoint.', e)
            }
        } else {
            url = annotation?.address()
        }

        url = url ?: getNameNoPostfix(implementor)
        return !url?.startsWith('/') ? '/' + url : url
    }

    private static String getNameNoPostfix(Object endpoint) {
        String className = endpoint?.class?.simpleName
        String url = ''
        if (className?.endsWith(ServiceArtefactHandler.TYPE)) {
            url = StringUtils.removeEnd(className, ServiceArtefactHandler.TYPE)
        }
        return '/' + url?.toLowerCase()
    }
}

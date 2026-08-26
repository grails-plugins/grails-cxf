package org.grails.cxf.utils

import org.apache.cxf.endpoint.AbstractEndpointFactory
import org.apache.cxf.jaxws.JaxWsServerFactoryBean

public enum EndpointType {
	JAX_WS(JaxWsServerFactoryBean.class);

	Class<? extends AbstractEndpointFactory> factoryBean;

	EndpointType(final Class<? extends AbstractEndpointFactory> factoryBean) {
		this.factoryBean = factoryBean;
	}

	static EndpointType forExpose(String expose) throws IllegalArgumentException {
		String name = expose.replaceAll(/(\s|\-)/, '_')
		try {
			return valueOf(name);
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
			//return EndpointExposureType.valueOf("JAX_WS");
		}
	}
}

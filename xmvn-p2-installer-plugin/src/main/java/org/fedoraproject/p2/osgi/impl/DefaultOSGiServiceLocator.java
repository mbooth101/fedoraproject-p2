/*******************************************************************************
 * Copyright (c) 2014 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2.osgi.impl;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import org.fedoraproject.p2.osgi.OSGiFramework;
import org.fedoraproject.p2.osgi.OSGiServiceLocator;

/**
 * @author Mikolaj Izdebski
 */
@Named
@Singleton
public class DefaultOSGiServiceLocator implements OSGiServiceLocator {
	private final OSGiFramework framework;

	@Inject
	public DefaultOSGiServiceLocator(OSGiFramework framework) {
		this.framework = framework;
	}

	@Override
	public <T> T getService(Class<T> clazz) {
		BundleContext context = framework.getBundleContext();

		ServiceReference<T> serviceReference = context
				.getServiceReference(clazz);

		if (serviceReference == null)
			throw new RuntimeException("OSGi service for " + clazz.getName()
					+ " was not found");

		return context.getService(serviceReference);
	}
}

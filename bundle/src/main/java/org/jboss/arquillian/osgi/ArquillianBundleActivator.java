/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.arquillian.osgi;

// $Id$

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

import org.jboss.arquillian.protocol.jmx.JMXTestRunner;
import org.jboss.arquillian.protocol.jmx.JMXTestRunner.TestClassLoader;
import org.jboss.arquillian.testenricher.osgi.BundleAssociation;
import org.jboss.arquillian.testenricher.osgi.BundleContextAssociation;
import org.jboss.logging.Logger;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleReference;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.SynchronousBundleListener;

/**
 * This is the Arquillian {@link BundleActivator}.
 *
 * It unconditionally starts the {@link JMXTestRunner}.
 *
 * @author thomas.diesler@jboss.com
 * @since 17-May-2009
 */
public class ArquillianBundleActivator implements BundleActivator, SynchronousBundleListener {
    // Provide logging
    private static Logger log = Logger.getLogger(ArquillianBundleActivator.class);

    private JMXTestRunner testRunner;

    private final Map<Bundle, Semaphore> bundleSemaphores = new ConcurrentHashMap<Bundle, Semaphore>();

    @Override
    public void start(final BundleContext context) throws Exception {
        final BundleContext sysContext = context.getBundle(0).getBundleContext();
        final TestClassLoader testClassLoader = new TestClassLoader() {
            @Override
            public Class<?> loadTestClass(final String className) throws ClassNotFoundException {
                for (final Entry<Bundle, Semaphore> entry : ArquillianBundleActivator.this.bundleSemaphores.entrySet()) {
                    final Bundle bundle = entry.getKey();
                    final Semaphore semaphore = entry.getValue();
                    try {
                        if (semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                            semaphore.release();
                        }
                        return bundle.loadClass(className);
                    } catch (final InterruptedException _) {
                    } catch (final ClassNotFoundException _) {
                    }
                }
                final Bundle arqBundle = context.getBundle();
                return arqBundle.loadClass(className);
            }
        };

        // Register the JMXTestRunner
        final MBeanServer mbeanServer = getMBeanServer(context);
        this.testRunner = new JMXTestRunner(testClassLoader) {

            @Override
            public byte[] runTestMethod(final String className, final String methodName) {
                Bundle bundle = null;
                try {
                    final Class<?> testClass = testClassLoader.loadTestClass(className);
                    final BundleReference bundleRef = (BundleReference) testClass.getClassLoader();
                    bundle = bundleRef.getBundle();
                } catch (final ClassNotFoundException e) {
                    // ignore
                }
                BundleAssociation.setBundle(bundle);
                BundleContextAssociation.setBundleContext(sysContext);
                return super.runTestMethod(className, methodName);
            }
        };
        this.testRunner.registerMBean(mbeanServer);
        context.addBundleListener(this);
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        context.removeBundleListener(this);
        // Unregister the JMXTestRunner
        final MBeanServer mbeanServer = getMBeanServer(context);
        this.testRunner.unregisterMBean(mbeanServer);
    }

    private MBeanServer getMBeanServer(final BundleContext context) {
        // Check if the MBeanServer is registered as an OSGi service
        final ServiceReference sref = context.getServiceReference(MBeanServer.class.getName());
        if (sref != null) {
            final MBeanServer mbeanServer = (MBeanServer) context.getService(sref);
            log.debug("Found MBeanServer fom service: " + mbeanServer.getDefaultDomain());
            return mbeanServer;
        }

        // Find or create the MBeanServer
        return findOrCreateMBeanServer();
    }

    private MBeanServer findOrCreateMBeanServer() {
        MBeanServer mbeanServer = null;

        final ArrayList<MBeanServer> serverArr = MBeanServerFactory.findMBeanServer(null);
        if (serverArr.size() > 1) {
            log.warn("Multiple MBeanServer instances: " + serverArr);
        }

        if (serverArr.size() > 0) {
            mbeanServer = serverArr.get(0);
            log.debug("Found MBeanServer: " + mbeanServer.getDefaultDomain());
        }

        if (mbeanServer == null) {
            log.debug("No MBeanServer, create one ...");
            mbeanServer = MBeanServerFactory.createMBeanServer();
        }

        return mbeanServer;
    }

    @Override
    public void bundleChanged(final BundleEvent event) {
        if (event.getBundle().getHeaders().get("ArquillianTestBundle") != null) {
            switch (event.getType()) {
                case BundleEvent.INSTALLED:
                    this.bundleSemaphores.put(event.getBundle(), new Semaphore(0));
                    break;
                case BundleEvent.RESOLVED:
                    this.bundleSemaphores.get(event.getBundle()).release();
                    break;
                case BundleEvent.UNINSTALLED:
                    this.bundleSemaphores.remove(event.getBundle()).release();
                    break;
            }
        }
    }
}

/*******************************************************************************
 * Copyright (c) 2022,2025 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package fish.payara.jakarta.data.core.cdi.extension;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.Producer;
import jakarta.enterprise.inject.spi.ProducerFactory;
import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RepositoryProducer<R> implements ProducerFactory<R>, Producer<R>{ 

    private static final Set<Annotation> QUALIFIERS = Set.of(Any.Literal.INSTANCE, Default.Literal.INSTANCE);

    private BeanManager beanMgr = null;
    private Set<Type> beanTypes = null;
    private JakartaDataExtension extension = null;
    private final Map<R, R> intercepted = new ConcurrentHashMap<>();
    private Class<?> repositoryInterface = null;
    
    RepositoryProducer(Class<?> repositoryInterface, BeanManager beanMgr,
                       JakartaDataExtension extension) {
        this.beanMgr = beanMgr;
        this.beanTypes = Set.of(repositoryInterface);
        this.extension = extension;
        this.repositoryInterface = repositoryInterface;
    }


    public <T> Producer<T> createProducer(Bean<T> bean) {
        return (Producer<T>) this;
    }

    @Override
    public Object produce(CreationalContext creationalContext) {
        @SuppressWarnings("unchecked")
        Class<R> repositoryInterface = (Class<R>) this.repositoryInterface;

        try {
            RepositoryImpl<?> handler = new RepositoryImpl<>(repositoryInterface);

            R instance = repositoryInterface.cast(Proxy.newProxyInstance(repositoryInterface.getClassLoader(),
                    new Class<?>[] { repositoryInterface },
                    handler));

            return instance;
        } catch (Throwable x) {
            throw x;
        }
    }

    @Override
    public void dispose(Object o) {

    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Set.of();
    }
}
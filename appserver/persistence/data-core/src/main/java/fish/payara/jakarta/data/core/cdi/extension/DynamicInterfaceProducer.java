package fish.payara.jakarta.data.core.cdi.extension;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.PassivationCapable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Set;

public class DynamicInterfaceProducer<T> implements Bean<T>, PassivationCapable {

    Class<T> repository;
    BeanManager beanManager;
    JakartaDataExtension jakartaDataExtension;
    private Set<Type> beanTypes = null;
    
    DynamicInterfaceProducer(Class<?> instance, BeanManager beanManager, JakartaDataExtension jakartaDataExtension) {
        this.repository = (Class<T>) instance;
        this.beanManager = beanManager;
        this.jakartaDataExtension = jakartaDataExtension;
        this.beanTypes = Set.of(instance);
    }
    
    @Override
    public Class<T> getBeanClass() {
        return repository;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet();
    }

    @Override
    public T create(CreationalContext<T> context) {
        Class<T> repositoryInterface = repository;
        RepositoryImpl<?> handler = new RepositoryImpl<>(repositoryInterface);
        return (T) Proxy.newProxyInstance(repositoryInterface.getClassLoader(), new Class[]{repositoryInterface},
                handler);
    }

    @Override
    public void destroy(T t, CreationalContext<T> creationalContext) {

    }

    @Override
    public Set<Type> getTypes() {
        return beanTypes;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return Set.of(Default.Literal.INSTANCE);
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return ApplicationScoped.class;
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.emptySet();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    @Override
    public String getId() {
        return this.repository.getName();
    }
}

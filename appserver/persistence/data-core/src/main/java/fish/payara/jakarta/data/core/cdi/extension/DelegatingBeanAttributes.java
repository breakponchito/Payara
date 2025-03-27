package fish.payara.jakarta.data.core.cdi.extension;

import jakarta.enterprise.inject.spi.BeanAttributes;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Set;

public class DelegatingBeanAttributes<T> implements BeanAttributes<T> {
    
    private final BeanAttributes<?> delegate;
    public DelegatingBeanAttributes(BeanAttributes<?> delegate) {
        super();
        Objects.requireNonNull(delegate);
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return this.delegate.getName();
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return this.delegate.getQualifiers();
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return this.delegate.getScope();
    }
    
    @Override
    public Set<Type> getTypes() {
        return this.delegate.getTypes();
    }
    
    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return this.delegate.getStereotypes();
    }

    @Override
    public boolean isAlternative() {
        return this.delegate.isAlternative();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}

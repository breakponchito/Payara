package fish.payara.jakarta.data.core.cdi.extension;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Predicate;

enum PersistenceRepositoryFilter implements Predicate<Class<?>> {

    INSTANCE;

    @Override
    public boolean test(Class<?> type) {
        Optional<Class<?>> entity = getEntityClass(type);
        return entity.map(this::toSupportedAnnotation)
                .isPresent();
    }

    private Annotation toSupportedAnnotation(Class<?> c) {
        return c.getAnnotation(jakarta.persistence.Entity.class);
    }

    private Optional<Class<?>> getEntityClass(Class<?> repository) {
        Type[] interfaces = repository.getGenericInterfaces();
        if (interfaces.length == 0) {
            return Optional.empty();
        }
        if (interfaces[0] instanceof ParameterizedType interfaceType) {
            return Optional.ofNullable(getEntityFromInterface(interfaceType));
        } else {
            return Optional.empty();
        }
    }

    private Class<?> getEntityFromInterface(ParameterizedType param) {
        Type[] arguments = param.getActualTypeArguments();
        if (arguments.length == 0) {
            return null;
        }
        Type argument = arguments[0];
        if (argument instanceof Class<?> entity) {
            return entity;
        }
        return null;
    }

}

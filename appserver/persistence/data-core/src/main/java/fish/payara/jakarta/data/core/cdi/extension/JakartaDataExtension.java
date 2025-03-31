package fish.payara.jakarta.data.core.cdi.extension;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.DataRepository;
import jakarta.data.repository.Repository;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class JakartaDataExtension implements Extension {

    private static final Logger logger = Logger.getLogger(JakartaDataExtension.class.getName());
    
    public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery, BeanManager manager) {
        logger.info("Finishing scanning process");
        Set<Class<?>> repositoryTypes = getRepositoryClasses();
        repositoryTypes.forEach(t -> {
            afterBeanDiscovery.addBean(new DynamicInterfaceProducer<>(t, manager, this));
        });
    }
    
    public Set<Class<?>> getRepositoryClasses() {
        Set<Class<?>> repositories = new HashSet<>();
        try (ScanResult result = new ClassGraph().enableAllInfo().scan()) {
            repositories.addAll(locateAndGetRepositories(result));
        }
        return repositories.stream().filter(cl -> {
                    List<Class<?>> interfaces = Arrays.asList(cl.getInterfaces());
                    return interfaces.contains(CrudRepository.class) || interfaces.contains(BasicRepository.class)
                            || interfaces.contains(DataRepository.class);
                }).collect(Collectors.toUnmodifiableSet());
    }
    
    private List<Class<DataRepository>> locateAndGetRepositories(ScanResult scan) {
        List<Class<DataRepository>> listDataRepository= scan.getClassesWithAnnotation(Repository.class).getInterfaces()
                .filter(ci -> ci.implementsInterface(DataRepository.class))
                .loadClasses(DataRepository.class);

        return listDataRepository.stream().filter(this::validate).toList();
    }

    public boolean validate(Class<?> type) {
        Optional<Class<?>> entity = getEntity(type);
        return entity.map(this::getSupportedAnnotation).isPresent();
    }

    private Annotation getSupportedAnnotation(Class<?> c) {
        return c.getAnnotation(jakarta.persistence.Entity.class);
    }
    
    private Optional<Class<?>> getEntity(Class<?> repository) {
        Type[] interfaces = repository.getGenericInterfaces();
        if (interfaces.length == 0) {
            return Optional.empty();
        }
        if (interfaces[0] instanceof ParameterizedType interfaceType) {
            return Optional.ofNullable(getEntityFromParametersInterface(interfaceType));
        } else {
            return Optional.empty();
        }
    }

    private Class<?> getEntityFromParametersInterface(ParameterizedType param) {
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

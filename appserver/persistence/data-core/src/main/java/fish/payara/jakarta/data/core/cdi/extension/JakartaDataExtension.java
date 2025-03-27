package fish.payara.jakarta.data.core.cdi.extension;

import fish.payara.jakarta.data.core.RepositoryService;
import fish.payara.jakarta.data.core.RepositoryServiceImpl;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.DataRepository;
import jakarta.data.repository.Repository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.ProcessProducer;
import jakarta.enterprise.inject.spi.Producer;
import jakarta.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.glassfish.internal.api.Globals;


public class JakartaDataExtension<E extends Member & AnnotatedElement> implements Extension {

    private static final Logger logger = Logger.getLogger(JakartaDataExtension.class.getName());
    
    @Inject
    private RepositoryServiceImpl handler;

    @Inject
    private RepositoryService h;

    void beforeBeanDiscovery(@Observes BeforeBeanDiscovery bbd, BeanManager manager) {
        bbd.addAnnotatedType(Repository.class, "Adding repository annotation").add(ApplicationScoped.Literal.INSTANCE);
    }

    public void validateInjectionPoint(@Observes ProcessInjectionPoint<?, ?> pip, BeanManager manager) {
        Class<?> cl = pip.getInjectionPoint().getMember().getDeclaringClass();
        if(cl.getName().contains("HelloResource")) {
            Class<?> interfaceClass = (Class)pip.getInjectionPoint().getType();
            if(interfaceClass.isInterface()) {
                Annotation[] annotations = interfaceClass.getAnnotations();
                for (Annotation annotation : annotations) {
                    if (annotation.annotationType().getName().equals("jakarta.data.repository.Repository")) {
                        //RepositoryProducer<Object> producer = new RepositoryProducer<>(interfaceClass, manager, this);
                        //Bean<Object> bean = manager.createBean(producer, (Class<Object>) interfaceClass, producer);
                    }
                }
            }
        }
        logger.info("Validating injection point " + cl.getName());
    }
    
    public void verifyProducer(@Observes ProcessProducer<?, ?> pip, BeanManager bm) {
        Repository repo = pip.getAnnotatedMember().getAnnotation(Repository.class);
        Producer<?> p = pip.getProducer();
        logger.info("Repository Found: " + repo);
        logger.info("Verifying producer " + p.getClass().getName() + " annotated member" + pip.getAnnotatedMember());
    }
    
    
    
    public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery, BeanManager manager) {
        logger.info("Finishing scanning process");
        logger.info("Starting scanning process");
        initService();
        
        /*for (Iterator<Class<?>> it = handler.getRepositoryMap().keySet().iterator(); it.hasNext(); ) {
            Class<?> repositoryInterface = it.next();
            logger.info(repositoryInterface.toString());
            Class<?>[] interfaces  = repositoryInterface.getInterfaces();
            Class<CrudRepository> crudRep = null;
            for (Class<?> i : interfaces) {
                if(i.getName().contains("CrudRepository")) {
                    
                }
            }
            afterBeanDiscovery.addBean();
        }*/
        Set<Class<?>> crudTypes = repositoriesStandard();
        crudTypes.forEach(type -> {
            afterBeanDiscovery.addBean(new DynamicInterfaceProducer<>(type, manager, this));
        });
    }

    public Set<Class<?>> repositoriesStandard() {
        Set<Class<?>> repositories = new HashSet<>();
        try (ScanResult result = new ClassGraph().enableAllInfo().scan()) {
            repositories.addAll(loadRepositories(result));
        }
        return repositories.stream()
                .filter(c -> {
                    List<Class<?>> interfaces = Arrays.asList(c.getInterfaces());
                    return interfaces.contains(CrudRepository.class)
                            || interfaces.contains(BasicRepository.class)
                            || interfaces.contains(DataRepository.class);
                }).collect(Collectors.toUnmodifiableSet());
    }
    
    private static List<Class<DataRepository>> loadRepositories(ScanResult scan) {
        return scan.getClassesWithAnnotation(Repository.class)
                .getInterfaces()
                .filter(c -> c.implementsInterface(DataRepository.class))
                .loadClasses(DataRepository.class)
                .stream().filter(PersistenceRepositoryFilter.INSTANCE)
                .toList();
    }

    @Dependent
    private static final Object produceGenericInterface(final InjectionPoint injectionPoint, Repository genericInterface) {
        Objects.requireNonNull(injectionPoint);
        Objects.requireNonNull(genericInterface);
        RepositoryImpl<?> handler = new RepositoryImpl<>(genericInterface.getClass());
        Object instance = Proxy.newProxyInstance(genericInterface.getClass().getClassLoader(),
                new Class<?>[]{genericInterface.getClass()},
                handler);
        return instance;
    }

    private void initService() {
        if (handler == null) {
            handler = Globals.getDefaultBaseServiceLocator().getService(RepositoryServiceImpl.class);
        }
    }
}
